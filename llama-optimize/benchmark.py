"""Measure inference speed and memory usage"""
import yaml, torch, time
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer

def benchmark_inference(model_path, label):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(model_path, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(model_path, torch_dtype=torch.float16 if device.type == "cuda" else torch.float32, trust_remote_code=True, device_map="auto" if device.type == "cuda" else None)
    model.eval()
    test_prompt = "Explain quantum computing in simple terms."
    inputs = tokenizer(test_prompt, return_tensors="pt").to(device)
    with torch.no_grad():
        _ = model.generate(**inputs, max_new_tokens=20, do_sample=False)
    num_runs, total_time, total_tokens = 5, 0, 0
    for _ in range(num_runs):
        if device.type == "cuda": torch.cuda.synchronize()
        start = time.time()
        with torch.no_grad():
            outputs = model.generate(**inputs, max_new_tokens=100, do_sample=False)
        if device.type == "cuda": torch.cuda.synchronize()
        elapsed = time.time() - start
        new_tokens = outputs.size(1) - inputs["input_ids"].size(1)
        total_time += elapsed; total_tokens += new_tokens
    tokens_per_sec = total_tokens / total_time
    model_size_mb = sum(p.numel() * p.element_size() for p in model.parameters()) / 1e6
    print(f"{label:12s} | Speed: {tokens_per_sec:6.1f} tok/s | Size: {model_size_mb:6.0f} MB")
    del model; torch.cuda.empty_cache()
    return tokens_per_sec, model_size_mb

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    print("=== Benchmark Comparison ===\n")
    results = {}
    if Path(config["model"]["local_path"]).exists():
        results["original"] = benchmark_inference(config["model"]["local_path"], "Original")
    if Path(config["model"]["output_path"]).exists():
        results["optimized"] = benchmark_inference(config["model"]["output_path"], "Optimized")
    print("\n=== Summary ===")
    for name, (speed, size) in results.items():
        print(f"{name:12s} | Speed: {speed:6.1f} tok/s | Size: {size:6.0f} MB")

if __name__ == "__main__":
    main()
