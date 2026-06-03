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
    model = AutoModelForCausalLM.from_pretrained(model_name, torch_dtype="auto", trust_remote_code=True, device_map="auto")
    model.save_pretrained(local_path)
    tokenizer.save_pretrained(local_path)
    print(f"Model saved to {local_path}")
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Total parameters: {total_params:,}")
    print(f"Estimated FP16 size: {total_params * 2 / 1e9:.1f} GB")

if __name__ == "__main__":
    main()
