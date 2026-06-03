import yaml, torch
from torch.utils.data import DataLoader
from tqdm import tqdm
import editdistance
from data.dataset import OcrDataset, collate_fn, CHARS, BLANK_IDX, NUM_CLASSES
from model.crnn import CRNN, ctc_decode

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = CRNN(img_channels=config["model"]["image_channels"], img_height=config["model"]["image_height"], num_classes=NUM_CLASSES, cnn_out=config["model"]["cnn_output_channels"], rnn_hidden=config["model"]["rnn_hidden_size"], rnn_layers=config["model"]["rnn_layers"]).to(device)
    model.load_state_dict(torch.load(config["output"]["model_dir"] + "/best_model.pt", map_location=device))
    model.eval()
    ds = OcrDataset(config["data"]["val_annotation"], img_height=config["model"]["image_height"])
    loader = DataLoader(ds, batch_size=1, shuffle=False, collate_fn=collate_fn)
    total_cer, correct = 0, 0
    with torch.no_grad():
        for images, texts, _, _ in tqdm(loader, desc="Evaluating"):
            images = images.to(device)
            logits = model(images)
            decoded = ctc_decode(logits, BLANK_IDX)
            target_text = "".join(CHARS[idx.item()] for idx in texts[0] if idx.item() < len(CHARS))
            pred_text = "".join(CHARS[i] for i in decoded[0] if i < len(CHARS))
            cer = editdistance.eval(pred_text, target_text) / max(len(target_text), 1)
            total_cer += cer
            if pred_text == target_text: correct += 1
    print(f"Average CER: {total_cer/len(ds):.4f}")
    print(f"Exact Match: {correct/len(ds)*100:.2f}%")

if __name__ == "__main__":
    main()
