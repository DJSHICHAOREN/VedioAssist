#!/bin/bash
set -e
MODEL_SOURCE="${1:-./models/qwen2.5-1.5b-optimized}"
LLAMA_CPP_DIR="${LLAMA_CPP_DIR:-./llama.cpp}"
QUANT_TYPE="${2:-Q4_K_M}"
echo "=== Step 1: Convert HF to GGUF FP16 ==="
python "$LLAMA_CPP_DIR/convert_hf_to_gguf.py" "$MODEL_SOURCE" --outfile "$MODEL_SOURCE/model-fp16.gguf" --outtype f16
echo "=== Step 2: Quantize to $QUANT_TYPE ==="
"$LLAMA_CPP_DIR/build/bin/quantize" "$MODEL_SOURCE/model-fp16.gguf" "$MODEL_SOURCE/model-${QUANT_TYPE}.gguf" "$QUANT_TYPE"
echo "=== Step 3: Size comparison ==="
echo "FP16 size:  $(du -sh "$MODEL_SOURCE/model-fp16.gguf" | cut -f1)"
echo "${QUANT_TYPE} size: $(du -sh "$MODEL_SOURCE/model-${QUANT_TYPE}.gguf" | cut -f1)"
echo "=== Done ==="
echo "Quantized model: $MODEL_SOURCE/model-${QUANT_TYPE}.gguf"
