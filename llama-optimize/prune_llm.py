"""Apply pruning to Qwen 2.5 1.5B model"""
import yaml, torch, torch.nn as nn
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer

def prune_attention_heads(model, layer_keep_ratio_start, layer_keep_ratio_end):
    total_layers = model.config.num_hidden_layers
    print(f"Found {total_layers} transformer layers")
    pruned_heads_total = 0
    for layer_idx in range(total_layers):
        ratio = layer_keep_ratio_start + (layer_keep_ratio_end - layer_keep_ratio_start) * (layer_idx / max(total_layers - 1, 1))
        prefixes = [
            f"model.layers.{layer_idx}.self_attn.q_proj",
            f"model.layers.{layer_idx}.self_attn.k_proj",
            f"model.layers.{layer_idx}.self_attn.v_proj",
            f"model.layers.{layer_idx}.self_attn.o_proj",
        ]
        for prefix in prefixes:
            for name, param in model.named_parameters():
                if name.startswith(prefix):
                    importance = param.data.norm(dim=1)
                    num_heads = param.data.size(0)
                    num_keep = max(1, int(num_heads * ratio))
                    _, indices = torch.topk(importance, num_keep)
                    mask = torch.zeros(num_heads, device=param.device)
                    mask[indices] = 1
                    param.data = param.data * mask.unsqueeze(1) if param.dim() == 2 else param.data * mask.view(-1, 1, 1)
                    pruned_heads_total += (num_heads - num_keep)
                    break
    print(f"Pruned {pruned_heads_total} attention head channels total")
    return model

def prune_layers(model, keep_ratio_end):
    total_layers = model.config.num_hidden_layers
    layers_to_keep = max(4, int(total_layers * keep_ratio_end))
    layers_to_prune = total_layers - layers_to_keep
    if layers_to_prune <= 0:
        print("No layers to prune"); return model
    print(f"Pruning {layers_to_prune} deepest layers (keeping {layers_to_keep}/{total_layers})")
    if hasattr(model, "model") and hasattr(model.model, "layers"):
        model.model.layers = model.model.layers[:layers_to_keep]
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
    model = AutoModelForCausalLM.from_pretrained(str(model_path), torch_dtype=torch.float16 if device.type == "cuda" else torch.float32, trust_remote_code=True, device_map="auto" if device.type == "cuda" else None)
    print("\nPruning attention heads...")
    model = prune_attention_heads(model, config["pruning"]["layer_keep_ratio"]["start"], config["pruning"]["layer_keep_ratio"]["end"])
    keep_ratio_end = config["pruning"]["layer_keep_ratio"]["end"]
    if keep_ratio_end < 1.0:
        print("\nPruning entire layers...")
        model = prune_layers(model, keep_ratio_end)
    print(f"\nSaving pruned model to {output_path}...")
    model.save_pretrained(output_path)
    tokenizer.save_pretrained(output_path)
    params_after = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"Parameters after pruning: {params_after:,}")
    print(f"Estimated FP16 size: {params_after * 2 / 1e9:.1f} GB")

if __name__ == "__main__":
    main()
