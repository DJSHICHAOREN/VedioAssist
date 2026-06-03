import yaml, torch, torch.nn as nn, torch.optim as optim
from torch.utils.data import DataLoader
from pathlib import Path
from tqdm import tqdm
import editdistance
from data.dataset import OcrDataset, collate_fn, CHARS, BLANK_IDX, NUM_CLASSES
from model.crnn import CRNN, ctc_decode

def train_one_epoch(model, loader, optimizer, criterion, device):
    model.train()
    total_loss = 0
    for images, texts, text_lengths, _ in tqdm(loader, desc="Training"):
        images, texts, text_lengths = images.to(device), texts.to(device), text_lengths.to(device)
        optimizer.zero_grad()
        logits = model(images)
        log_probs = nn.functional.log_softmax(logits, dim=2).permute(1, 0, 2)
        input_lengths = torch.full((images.size(0),), log_probs.size(0), dtype=torch.long)
        loss = criterion(log_probs, texts, input_lengths, text_lengths)
        loss.backward(); optimizer.step()
        total_loss += loss.item()
    return total_loss / len(loader)

def validate(model, loader, device):
    model.eval()
    total_cer = 0
    with torch.no_grad():
        for images, texts, _, _ in tqdm(loader, desc="Validating"):
            images = images.to(device)
            logits = model(images)
            decoded = ctc_decode(logits, BLANK_IDX)
            for pred_indices, target in zip(decoded, texts):
                pred_text = "".join(CHARS[i] for i in pred_indices if i < len(CHARS))
                target_text = "".join(CHARS[idx.item()] for idx in target if idx.item() < len(CHARS))
                cer = editdistance.eval(pred_text, target_text) / max(len(target_text), 1)
                total_cer += cer
    return total_cer / len(texts) if len(texts) > 0 else 1.0

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")
    train_ds = OcrDataset(config["data"]["train_annotation"], img_height=config["model"]["image_height"], augment=True)
    val_ds = OcrDataset(config["data"]["val_annotation"], img_height=config["model"]["image_height"], augment=False)
    train_loader = DataLoader(train_ds, batch_size=config["training"]["batch_size"], shuffle=True, collate_fn=collate_fn, num_workers=4)
    val_loader = DataLoader(val_ds, batch_size=config["training"]["batch_size"], shuffle=False, collate_fn=collate_fn, num_workers=4)
    print(f"Train samples: {len(train_ds)}, Val samples: {len(val_ds)}")
    model = CRNN(img_channels=config["model"]["image_channels"], img_height=config["model"]["image_height"], num_classes=NUM_CLASSES, cnn_out=config["model"]["cnn_output_channels"], rnn_hidden=config["model"]["rnn_hidden_size"], rnn_layers=config["model"]["rnn_layers"]).to(device)
    criterion = nn.CTCLoss(blank=BLANK_IDX, zero_infinity=True)
    optimizer = optim.AdamW(model.parameters(), lr=config["training"]["learning_rate"], weight_decay=config["training"]["weight_decay"])
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=5)
    Path(config["output"]["model_dir"]).mkdir(parents=True, exist_ok=True)
    best_cer = float("inf"); patience_counter = 0
    for epoch in range(config["training"]["epochs"]):
        print(f"\nEpoch {epoch+1}/{config['training']['epochs']}")
        train_loss = train_one_epoch(model, train_loader, optimizer, criterion, device)
        val_cer = validate(model, val_loader, device)
        print(f"Train Loss: {train_loss:.4f}, Val CER: {val_cer:.4f}")
        scheduler.step(val_cer)
        if val_cer < best_cer:
            best_cer = val_cer; patience_counter = 0
            torch.save(model.state_dict(), Path(config["output"]["model_dir"]) / "best_model.pt")
            print(f"Saved best model with CER={best_cer:.4f}")
        else:
            patience_counter += 1
        if patience_counter >= config["training"]["early_stop_patience"]:
            print("Early stopping"); break
    print(f"Training complete. Best CER: {best_cer:.4f}")

if __name__ == "__main__":
    main()
