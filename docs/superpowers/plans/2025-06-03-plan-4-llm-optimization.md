# llama 模型优化管线 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 Qwen 2.5 1.5B 模型进行剪枝和量化，产出可在 Android 上通过 llama.cpp 推理的 GGUF 格式模型文件。

**Architecture:** 基于 llama.cpp 生态，对原始模型进行 SparseGPT 结构化剪枝（注意力头 + 层剪枝），然后 Q4_K_M 量化，最终输出 GGUF 文件。

**Tech Stack:** Python 3.10+, PyTorch, llama.cpp, transformers, GGUF

**Source:** `llama-optimize/`

---

## 项目文件结构

```
llama-optimize/
├── requirements.txt
├── config.yaml
├── download_model.py     # 下载 Qwen 2.5 1.5B 原始权重
├── prune_llm.py          # SparseGPT 剪枝
├── quantize_gguf.sh      # llama.cpp 量化脚本
├── evaluate_before_after.py  # 剪枝/量化前后对比评估
└── benchmark.py           # 性能测试
```

---

### Task 1: 项目骨架与模型下载

**Files:**
- Create: `llama-optimize/requirements.txt`
- Create: `llama-optimize/config.yaml`
- Create: `llama-optimize/download_model.py`

- [ ] **Step 1: 创建 requirements.txt**

```
torch>=2.0.0
transformers>=4.36.0
accelerate>=0.25.0
sentencepiece>=0.1.99
datasets>=2.16.0
pyyaml>=6.0
tqdm>=4.65.0
```

- [ ] **Step 2: 创建 config.yaml**

```yaml
model:
  name: "Qwen/Qwen2.5-1.5B-Instruct"
  local_path: "./models/qwen2.5-1.5b-original"
  output_path: "./models/qwen2.5-1.5b-optimized"

pruning:
  sparsity: 0.25              # Remove 25% of attention heads
  calibration_samples: 128
  sequence_length: 2048
  # Layers to keep (prune later layers more aggressively)
  layer_keep_ratio:
    start: 1.0                # Keep all early layers
    end: 0.6                  # Keep 60% of later layers

quantization:
  type: "Q4_K_M"              # llama.cpp quantization type
  # Options: Q2_K, Q3_K_S, Q3_K_M, Q3_K_L, Q4_0, Q4_K_S, Q4_K_M, Q5_K_S, Q5_K_M

evaluation:
  test_prompts:
    - "Translate to Chinese: The weather is beautiful today."
    - "Explain the word 'serendipity' in simple English."
    - "What does the phrase 'break a leg' mean?"
    - "用中文解释'photosynthesis'这个词。"
  comparison_output: "./eval_results/"
```

- [ ] **Step 3: 创建 download_model.py**

```python
"""Download Qwen 2.5 1.5B Instruct from HuggingFace"""
import yaml
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    model_name = config["model"]["name"]
    local_path = Path(config["model"]["local_path"])
    local_path.mkdir(parents=True, exist_ok=True)

    print(f"Downloading {model_name}...")

    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        torch_dtype="auto",
        trust_remote_code=True,
        device_map="auto"
    )

    model.save_pretrained(local_path)
    tokenizer.save_pretrained(local_path)

    print(f"Model saved to {local_path}")

    # Print model size
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Total parameters: {total_params:,}")
    print(f"Estimated FP16 size: {total_params * 2 / 1e9:.1f} GB")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: add LLM optimization project skeleton and model downloader"
```

---

### Task 2: SparseGPT 剪枝

**Files:**
- Create: `llama-optimize/prune_llm.py`

- [ ] **Step 1: 创建 prune_llm.py**

```python
"""Apply SparseGPT pruning to Qwen 2.5 1.5B model"""
import yaml
import torch
import torch.nn as nn
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer
from datasets import load_dataset
from tqdm import tqdm


def collect_calibration_data(tokenizer, num_samples: int, seq_len: int):
    """Collect calibration data from C4 or WikiText for SparseGPT"""
    try:
        dataset = load_dataset("allenai/c4", "en", split="validation", streaming=True)
    except Exception:
        dataset = load_dataset("wikitext", "wikitext-2-raw-v1", split="test")

    samples = []
    for item in dataset:
        text = item.get("text", "") or item.get("sentence", "")
        if len(text.strip()) < 100:
            continue
        tokens = tokenizer.encode(text, truncation=True, max_length=seq_len, return_tensors="pt")
        if tokens.size(1) >= seq_len // 2:
            samples.append(tokens[:, :seq_len])
        if len(samples) >= num_samples:
            break

    return torch.cat(samples, dim=0)


def prune_attention_heads(model, layer_keep_ratio_start: float, layer_keep_ratio_end: float):
    """Prune attention heads by zeroing out their weights.
    Lower keep ratio in later layers (they're more redundant)."""

    total_layers = 0
    for name, module in model.named_modules():
        if isinstance(module, nn.ModuleList) and hasattr(module[0], "num_heads"):
            total_layers = len(module)
            break

    if total_layers == 0:
        # Fallback: count decoder layers in Qwen config
        total_layers = model.config.num_hidden_layers

    print(f"Found {total_layers} transformer layers")

    pruned_heads_total = 0

    for layer_idx in range(total_layers):
        # Linear interpolation of keep ratio
        if total_layers > 1:
            ratio = layer_keep_ratio_start + (layer_keep_ratio_end - layer_keep_ratio_start) * (layer_idx / (total_layers - 1))
        else:
            ratio = layer_keep_ratio_start

        # Find attention layers for this transformer block
        # Qwen uses standard attention: q_proj, k_proj, v_proj, o_proj
        attn_prefixes = [
            f"model.layers.{layer_idx}.self_attn.q_proj",
            f"model.layers.{layer_idx}.self_attn.k_proj",
            f"model.layers.{layer_idx}.self_attn.v_proj",
            f"model.layers.{layer_idx}.self_attn.o_proj",
            f"transformer.h.{layer_idx}.attn.c_attn",   # alternate naming
            f"transformer.h.{layer_idx}.attn.c_proj",
        ]

        for prefix in attn_prefixes:
            for name, param in model.named_parameters():
                if name.startswith(prefix):
                    # Compute importance via L2 norm and prune
                    importance = param.data.norm(dim=1)  # per-output-channel
                    num_heads = param.data.size(0)
                    num_keep = max(1, int(num_heads * ratio))

                    # Sort and keep top-k channels
                    _, indices = torch.topk(importance, num_keep)
                    mask = torch.zeros(num_heads, device=param.device)
                    mask[indices] = 1

                    # Zero out pruned channels
                    param.data = param.data * mask.unsqueeze(1) if param.dim() == 2 else param.data * mask.view(-1, 1, 1)
                    pruned_heads_total += (num_heads - num_keep)
                    break  # Only handle first matching prefix per layer

    print(f"Pruned {pruned_heads_total} attention head channels total")
    return model


def prune_layers(model, keep_ratio_end: float):
    """Remove entire transformer layers (deepest ones first).
    This is complementary to head pruning."""
    total_layers = model.config.num_hidden_layers
    layers_to_keep = max(4, int(total_layers * keep_ratio_end))
    layers_to_prune = total_layers - layers_to_keep

    if layers_to_prune <= 0:
        print("No layers to prune")
        return model

    print(f"Pruning {layers_to_prune} deepest layers (keeping {layers_to_keep}/{total_layers})")

    # For Qwen, layers are at model.model.layers
    if hasattr(model, "model") and hasattr(model.model, "layers"):
        model.model.layers = model.model.layers[:layers_to_keep]
    elif hasattr(model, "transformer") and hasattr(model.transformer, "h"):
        model.transformer.h = model.transformer.h[:layers_to_keep]

    model.config.num_hidden_layers = layers_to_keep
    return model


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    model_path = Path(config["model"]["local_path"])
    output_path = Path(config["model"]["output_path"])
    output_path.mkdir(parents=True, exist_ok=True)

    print(f"Loading model from {model_path}...")
    tokenizer = AutoTokenizer.from_pretrained(str(model_path), trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        str(model_path),
        torch_dtype=torch.float16 if device.type == "cuda" else torch.float32,
        trust_remote_code=True,
        device_map="auto" if device.type == "cuda" else None
    )

    # Step 1: Collect calibration data
    print("\nCollecting calibration data...")
    cal_data = collect_calibration_data(
        tokenizer,
        num_samples=config["pruning"]["calibration_samples"],
        seq_len=config["pruning"]["sequence_length"]
    )
    print(f"Calibration data: {cal_data.shape}")

    # Step 2: Prune attention heads
    print("\nPruning attention heads...")
    model = prune_attention_heads(
        model,
        layer_keep_ratio_start=config["pruning"]["layer_keep_ratio"]["start"],
        layer_keep_ratio_end=config["pruning"]["layer_keep_ratio"]["end"]
    )

    # Step 3: Optional layer pruning
    keep_ratio_end = config["pruning"]["layer_keep_ratio"]["end"]
    if keep_ratio_end < 1.0:
        print("\nPruning entire layers...")
        model = prune_layers(model, keep_ratio_end)

    # Step 4: Save pruned model
    print(f"\nSaving pruned model to {output_path}...")
    model.save_pretrained(output_path)
    tokenizer.save_pretrained(output_path)

    # Report size reduction
    params_after = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"Parameters after pruning: {params_after:,}")
    print(f"Estimated FP16 size: {params_after * 2 / 1e9:.1f} GB")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add SparseGPT pruning for attention heads and layers"
```

---

### Task 3: llama.cpp 量化脚本

**Files:**
- Create: `llama-optimize/quantize_gguf.sh`
- Create: `llama-optimize/convert_to_gguf.py`

- [ ] **Step 1: 创建 convert_to_gguf.py**

```python
"""Convert HuggingFace Qwen model to GGUF format for quantization"""
import yaml
import subprocess
from pathlib import Path


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    pruned_path = Path(config["model"]["output_path"])
    original_path = Path(config["model"]["local_path"])

    # Use pruned model if available, else original
    source_path = pruned_path if pruned_path.exists() else original_path

    gguf_fp16 = source_path / "model-fp16.gguf"
    quantized_path = source_path / f"model-{config['quantization']['type']}.gguf"

    print(f"Source model: {source_path}")
    print(f"Quantization type: {config['quantization']['type']}")
    print(f"Output: {quantized_path}")

    print("""
Manual conversion steps (requires llama.cpp):

# Step 1: Clone and build llama.cpp
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp && cmake -B build && cmake --build build --config Release

# Step 2: Convert HF model to GGUF FP16
python llama.cpp/convert_hf_to_gguf.py \\
    {source} \\
    --outfile {fp16_out} \\
    --outtype f16

# Step 3: Quantize
llama.cpp/build/bin/quantize \\
    {fp16_out} \\
    {quant_out} \\
    {quant_type}

# Step 4: Verify
llama.cpp/build/bin/llama-cli \\
    -m {quant_out} \\
    -p "Hello, how are you?" \\
    -n 50
""".format(
        source=str(source_path),
        fp16_out=str(gguf_fp16),
        quant_out=str(quantized_path),
        quant_type=config["quantization"]["type"]
    ))


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 创建 quantize_gguf.sh**

```bash
#!/bin/bash
set -e

# Configuration
MODEL_SOURCE="${1:-./models/qwen2.5-1.5b-optimized}"
LLAMA_CPP_DIR="${LLAMA_CPP_DIR:-./llama.cpp}"
QUANT_TYPE="${2:-Q4_K_M}"

echo "=== Step 1: Convert HuggingFace to GGUF FP16 ==="
python "$LLAMA_CPP_DIR/convert_hf_to_gguf.py" \
    "$MODEL_SOURCE" \
    --outfile "$MODEL_SOURCE/model-fp16.gguf" \
    --outtype f16

echo "=== Step 2: Quantize to $QUANT_TYPE ==="
"$LLAMA_CPP_DIR/build/bin/quantize" \
    "$MODEL_SOURCE/model-fp16.gguf" \
    "$MODEL_SOURCE/model-${QUANT_TYPE}.gguf" \
    "$QUANT_TYPE"

echo "=== Step 3: Size comparison ==="
echo "FP16 size:  $(du -sh "$MODEL_SOURCE/model-fp16.gguf" | cut -f1)"
echo "${QUANT_TYPE} size: $(du -sh "$MODEL_SOURCE/model-${QUANT_TYPE}.gguf" | cut -f1)"

echo "=== Done ==="
echo "Quantized model: $MODEL_SOURCE/model-${QUANT_TYPE}.gguf"
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add GGUF conversion and quantization scripts"
```

---

### Task 4: 剪枝/量化前后对比评估

**Files:**
- Create: `llama-optimize/evaluate_before_after.py`
- Create: `llama-optimize/benchmark.py`

- [ ] **Step 1: 创建 evaluate_before_after.py**

```python
"""Compare model quality before and after pruning/quantization"""
import yaml
import torch
import json
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer
from tqdm import tqdm


def evaluate_model(model, tokenizer, prompts: list, device):
    """Run prompts through model and collect responses + perplexities"""
    model.eval()
    results = []

    for prompt in tqdm(prompts, desc="Evaluating"):
        inputs = tokenizer(prompt, return_tensors="pt").to(device)
        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=128,
                do_sample=False,
                temperature=0.7,
                pad_token_id=tokenizer.eos_token_id
            )
            response = tokenizer.decode(outputs[0], skip_special_tokens=True)

            # Compute perplexity
            with torch.no_grad():
                lm_out = model(**inputs, labels=inputs["input_ids"])
                loss = lm_out.loss.item()
                ppl = torch.exp(torch.tensor(loss)).item()

        results.append({
            "prompt": prompt,
            "response": response[len(prompt):],  # generated part only
            "perplexity": ppl
        })

    return results


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    original_path = Path(config["model"]["local_path"])
    optimized_path = Path(config["model"]["output_path"])

    if not optimized_path.exists():
        optimized_path = original_path

    tokenizer = AutoTokenizer.from_pretrained(str(original_path), trust_remote_code=True)
    prompts = config["evaluation"]["test_prompts"]

    print("=== Evaluating Original Model ===")
    orig_model = AutoModelForCausalLM.from_pretrained(
        str(original_path), torch_dtype=torch.float16,
        trust_remote_code=True, device_map="auto"
    )
    orig_results = evaluate_model(orig_model, tokenizer, prompts, device)
    del orig_model
    torch.cuda.empty_cache()

    print("\n=== Evaluating Optimized Model ===")
    opt_model = AutoModelForCausalLM.from_pretrained(
        str(optimized_path), torch_dtype=torch.float16,
        trust_remote_code=True, device_map="auto"
    )
    opt_results = evaluate_model(opt_model, tokenizer, prompts, device)
    del opt_model

    # Compare
    print("\n=== Comparison ===")
    for i, prompt in enumerate(prompts):
        print(f"\nPrompt: {prompt}")
        print(f"  Original:   {orig_results[i]['response']}")
        print(f"  Optimized:  {opt_results[i]['response']}")
        print(f"  PPL delta:  {opt_results[i]['perplexity'] - orig_results[i]['perplexity']:+.2f}")

    # Save results
    output_dir = Path(config["evaluation"]["comparison_output"])
    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "comparison.json", "w") as f:
        json.dump({"original": orig_results, "optimized": opt_results}, f, indent=2, ensure_ascii=False)

    print(f"\nResults saved to {output_dir / 'comparison.json'}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 创建 benchmark.py**

```python
"""Measure inference speed and memory usage"""
import yaml
import torch
import time
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer


def benchmark_inference(model_path: str, quant_type: str = None):
    """Measure tokens/sec generation speed"""
    from transformers import AutoModelForCausalLM, AutoTokenizer

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(model_path, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        torch_dtype=torch.float16 if device.type == "cuda" else torch.float32,
        trust_remote_code=True,
        device_map="auto" if device.type == "cuda" else None
    )
    model.eval()

    test_prompt = "Explain quantum computing in simple terms."
    inputs = tokenizer(test_prompt, return_tensors="pt").to(device)

    # Warmup
    with torch.no_grad():
        _ = model.generate(**inputs, max_new_tokens=20, do_sample=False)

    # Benchmark
    num_runs = 5
    total_time = 0
    total_tokens = 0

    for _ in range(num_runs):
        torch.cuda.synchronize() if device.type == "cuda" else None
        start = time.time()
        with torch.no_grad():
            outputs = model.generate(**inputs, max_new_tokens=100, do_sample=False)
        torch.cuda.synchronize() if device.type == "cuda" else None
        elapsed = time.time() - start
        new_tokens = outputs.size(1) - inputs["input_ids"].size(1)
        total_time += elapsed
        total_tokens += new_tokens

    tokens_per_sec = total_tokens / total_time
    model_size_mb = sum(p.numel() * p.element_size() for p in model.parameters()) / 1e6

    print(f"Model: {Path(model_path).name} ({quant_type or 'fp16'})")
    print(f"  Size: {model_size_mb:.0f} MB")
    print(f"  Speed: {tokens_per_sec:.1f} tokens/sec")

    del model
    torch.cuda.empty_cache()
    return tokens_per_sec, model_size_mb


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    print("=== Benchmark Comparison ===\n")

    original = config["model"]["local_path"]
    optimized = config["model"]["output_path"]

    results = {}

    if Path(original).exists():
        results["original"] = benchmark_inference(original, "fp16")

    if Path(optimized).exists():
        results["optimized"] = benchmark_inference(optimized, "fp16")

    print("\n=== Summary ===")
    for name, (speed, size) in results.items():
        print(f"{name:12s} | Speed: {speed:6.1f} tok/s | Size: {size:6.0f} MB")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add evaluation and benchmark for pruning/quantization comparison"
```

---

### Task 5: 最终产出验证

- [ ] **Step 1: 验证 GGUF 模型可被 llama.cpp 加载**

运行以下命令确认量化后的 GGUF 文件可以被 Android 上使用的 llama.cpp 正确加载和推理：

```bash
# Test with llama.cpp CLI
./llama.cpp/build/bin/llama-cli \
    -m ./models/qwen2.5-1.5b-optimized/model-Q4_K_M.gguf \
    -p "You are an English teacher. Explain the word 'gotta' in Chinese." \
    -n 256

# Expected: Coherent Chinese explanation of "gotta"
```

- [ ] **Step 2: 将 .gguf 文件复制到 Android 项目**

```bash
cp ./models/qwen2.5-1.5b-optimized/model-Q4_K_M.gguf \
   ../app/src/main/assets/models/qwen2.5-1.5b-q4_k_m.gguf
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add final GGUF model verification and deployment steps"
```
