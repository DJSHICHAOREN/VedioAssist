import yaml, torch
from pathlib import Path
from model.crnn import CRNN
from data.dataset import NUM_CLASSES

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    model = CRNN(img_channels=config["model"]["image_channels"], img_height=config["model"]["image_height"], num_classes=NUM_CLASSES, cnn_out=config["model"]["cnn_output_channels"], rnn_hidden=config["model"]["rnn_hidden_size"], rnn_layers=config["model"]["rnn_layers"])
    model.eval()
    pruned_path = Path(config["output"]["model_dir"]) / "pruned_model.pt"
    model_path = pruned_path if pruned_path.exists() else Path(config["output"]["model_dir"]) / "best_model.pt"
    model.load_state_dict(torch.load(model_path, map_location="cpu"))
    onnx_path = config["output"]["onnx_model"]
    dummy_input = torch.randn(1, 1, 32, 200)
    torch.onnx.export(model, dummy_input, onnx_path, input_names=["input"], output_names=["output"], dynamic_axes={"input": {3: "width"}, "output": {1: "sequence_length"}}, opset_version=13)
    print(f"ONNX model exported to {onnx_path}")

if __name__ == "__main__":
    main()
