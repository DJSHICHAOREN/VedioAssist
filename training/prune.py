import yaml, torch, torch.nn.utils.prune as prune, torch.nn as nn
from pathlib import Path
from torch.utils.data import DataLoader
from data.dataset import OcrDataset, collate_fn, NUM_CLASSES
from model.crnn import CRNN
from train import train_one_epoch, validate

def prune_model(model, sparsity):
    for name, module in model.named_modules():
        if isinstance(module, nn.Conv2d) and module.out_channels > 32:
            prune.ln_structured(module, name="weight", amount=sparsity, n=2, dim=0)
    return model

def remove_pruning_reparam(model):
    for name, module in model.named_modules():
        if isinstance(module, nn.Conv2d):
            try: prune.remove(module, "weight")
            except: pass
    return model

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model_path = Path(config["output"]["model_dir"]) / "best_model.pt"
    distilled_path = Path(config["output"]["model_dir"]) / "distilled_model.pt"
    if distilled_path.exists(): model_path = distilled_path
    model = CRNN(img_channels=config["model"]["image_channels"], img_height=config["model"]["image_height"], num_classes=NUM_CLASSES, cnn_out=config["model"]["cnn_output_channels"], rnn_hidden=config["model"]["rnn_hidden_size"], rnn_layers=config["model"]["rnn_layers"]).to(device)
    model.load_state_dict(torch.load(model_path, map_location=device))
    model = prune_model(model, config["pruning"]["target_sparsity"])
    model = remove_pruning_reparam(model)
    train_ds = OcrDataset(config["data"]["train_annotation"], img_height=config["model"]["image_height"], augment=True)
    train_loader = DataLoader(train_ds, batch_size=config["training"]["batch_size"], shuffle=True, collate_fn=collate_fn, num_workers=4)
    val_ds = OcrDataset(config["data"]["val_annotation"], img_height=config["model"]["image_height"])
    val_loader = DataLoader(val_ds, batch_size=config["training"]["batch_size"], shuffle=False, collate_fn=collate_fn, num_workers=4)
    criterion = nn.CTCLoss(blank=41, zero_infinity=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-4)
    for epoch in range(config["pruning"]["finetune_epochs"]):
        loss = train_one_epoch(model, train_loader, optimizer, criterion, device)
        cer = validate(model, val_loader, device)
        print(f"Finetune Epoch {epoch+1}: Loss={loss:.4f}, CER={cer:.4f}")
    out_path = Path(config["output"]["model_dir"]) / "pruned_model.pt"
    torch.save(model.state_dict(), out_path)
    print(f"Pruned model saved to {out_path}")

if __name__ == "__main__":
    main()
