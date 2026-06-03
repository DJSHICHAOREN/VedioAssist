"""Guide for converting HF model to GGUF format for quantization"""
import yaml
from pathlib import Path

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    pruned_path = Path(config["model"]["output_path"])
    original_path = Path(config["model"]["local_path"])
    source_path = pruned_path if pruned_path.exists() else original_path
    gguf_fp16 = str(source_path / "model-fp16.gguf")
    quantized_path = str(source_path / f"model-{config['quantization']['type']}.gguf")

    print(f"""
Manual conversion steps (requires llama.cpp):

# Step 1: Clone and build llama.cpp
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp && cmake -B build && cmake --build build --config Release

# Step 2: Convert HF model to GGUF FP16
python llama.cpp/convert_hf_to_gguf.py \\
    {source_path} \\
    --outfile {gguf_fp16} \\
    --outtype f16

# Step 3: Quantize
llama.cpp/build/bin/quantize \\
    {gguf_fp16} \\
    {quantized_path} \\
    {config['quantization']['type']}

# Step 4: Verify
llama.cpp/build/bin/llama-cli \\
    -m {quantized_path} \\
    -p "Hello, how are you?" \\
    -n 50

# Step 5: Copy to Android project
cp {quantized_path} ../app/src/main/assets/models/qwen2.5-1.5b-q4_k_m.gguf
""")

if __name__ == "__main__":
    main()
