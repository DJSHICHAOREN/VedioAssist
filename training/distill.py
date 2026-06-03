import yaml, torch
from torch.utils.data import DataLoader
from pathlib import Path
from tqdm import tqdm
from data.dataset import OcrDataset, collate_fn, NUM_CLASSES
from model.crnn import CRNN
from model.teacher_model import TrOCRTeacher, distillation_loss

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    student = CRNN(img_channels=config["model"]["image_channels"], img_height=config["model"]["image_height"], num_classes=NUM_CLASSES, cnn_out=config["model"]["cnn_output_channels"], rnn_hidden=config["model"]["rnn_hidden_size"], rnn_layers=config["model"]["rnn_layers"]).to(device)
    teacher = TrOCRTeacher(model_name=config["distillation"]["teacher_model"], num_classes=NUM_CLASSES).to(device)
    teacher.eval()
    train_ds = OcrDataset(config["data"]["train_annotation"], img_height=config["model"]["image_height"], augment=True)
    train_loader = DataLoader(train_ds, batch_size=config["training"]["batch_size"], shuffle=True, collate_fn=collate_fn, num_workers=4)
    temperature, alpha = config["distillation"]["temperature"], config["distillation"]["alpha"]
    optimizer = torch.optim.AdamW(student.parameters(), lr=config["training"]["learning_rate"])
    for epoch in range(20):
        student.train(); epoch_loss = 0
        for images, texts, text_lengths, _ in tqdm(train_loader, desc=f"Distill Epoch {epoch+1}"):
            images, texts, text_lengths = images.to(device), texts.to(device), text_lengths.to(device)
            with torch.no_grad():
                teacher_logits = teacher(images)
            student_logits = student(images)
            input_lengths = torch.full((images.size(0),), student_logits.size(1), dtype=torch.long).to(device)
            optimizer.zero_grad()
            loss = distillation_loss(student_logits, teacher_logits, texts, input_lengths, text_lengths, temperature, alpha)
            loss.backward(); optimizer.step()
            epoch_loss += loss.item()
        print(f"Epoch {epoch+1} Loss: {epoch_loss/len(train_loader):.4f}")
    out_path = Path(config["output"]["model_dir"]) / "distilled_model.pt"
    torch.save(student.state_dict(), out_path)
    print(f"Distilled model saved to {out_path}")

if __name__ == "__main__":
    main()
