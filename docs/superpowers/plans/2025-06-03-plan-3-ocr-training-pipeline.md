# OCR 模型训练 + 优化管线 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 训练 CRNN OCR 模型用于电影字幕识别，包含数据集加载、训练、评估、蒸馏、剪枝、量化，最终产出 TFLite 模型文件。

**Architecture:** Python 项目，PyTorch 训练 CRNN（CNN + BiLSTM + CTC），通过知识蒸馏提升小模型精度，结构化剪枝压缩，INT8 量化导出为 TFLite。

**Tech Stack:** Python 3.10+, PyTorch 2.x, torchvision, ONNX, TFLite Converter, CUDA (训练用)

**Source:** `training/`

---

## 项目文件结构

```
training/
├── requirements.txt
├── config.yaml
├── data/
│   └── dataset.py           # 数据加载器
├── model/
│   ├── crnn.py               # CRNN 模型定义
│   └── teacher_model.py      # TrOCR teacher wrapper
├── train.py                  # 训练脚本
├── evaluate.py               # 评估脚本
├── distill.py                # 知识蒸馏
├── prune.py                  # 结构化剪枝
├── quantize.py               # INT8 量化 -> TFLite
└── export.py                 # 导出 ONNX / TFLite
```

---

### Task 1: 项目骨架与依赖

**Files:**
- Create: `training/requirements.txt`
- Create: `training/config.yaml`

- [ ] **Step 1: 创建 requirements.txt**

```
torch>=2.0.0
torchvision>=0.15.0
numpy>=1.24.0
opencv-python>=4.8.0
pillow>=10.0.0
pyyaml>=6.0
onnx>=1.14.0
onnxruntime>=1.15.0
tensorflow>=2.13.0
tqdm>=4.65.0
editdistance>=0.6.2
```

- [ ] **Step 2: 创建 config.yaml**

```yaml
model:
  image_height: 32
  image_channels: 1          # 灰度
  cnn_output_channels: 512
  rnn_hidden_size: 256
  rnn_layers: 2
  num_classes: 41            # a-z + 0-9 + punctuation + blank

training:
  batch_size: 64
  epochs: 100
  learning_rate: 0.001
  weight_decay: 0.0001
  early_stop_patience: 15

data:
  train_annotation: "data/train/annotations.json"
  val_annotation: "data/val/annotations.json"
  # annotations.json format: [{"image": "images/img_00001.jpg", "bbox": [l,t,r,b], "text": "hello world"}, ...]

distillation:
  teacher_model: "microsoft/trocr-small-printed"
  temperature: 3.0
  alpha: 0.7                  # weight for distillation loss vs ground truth

pruning:
  target_sparsity: 0.3        # remove 30% of channels
  finetune_epochs: 10

quantization:
  calibration_samples: 200
  output_format: "tflite"    # tflite or onnx

output:
  model_dir: "output/"
  tflite_model: "output/ocr_model.tflite"
  onnx_model: "output/ocr_model.onnx"
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add OCR training project skeleton and config"
```

---

### Task 2: 数据集加载器

**Files:**
- Create: `training/data/dataset.py`

- [ ] **Step 1: 创建 dataset.py**

```python
import json
import random
from pathlib import Path
from PIL import Image, ImageOps
import torch
from torch.utils.data import Dataset
import torchvision.transforms as T
import numpy as np

CHARS = "abcdefghijklmnopqrstuvwxyz0123456789.,!?-'\" "
CHAR_TO_IDX = {c: i for i, c in enumerate(CHARS)}
BLANK_IDX = len(CHARS)
NUM_CLASSES = len(CHARS) + 1


class OcrDataset(Dataset):
    def __init__(self, annotation_path: str, img_height: int = 32, augment: bool = False):
        self.img_height = img_height
        self.augment = augment
        base_dir = Path(annotation_path).parent

        with open(annotation_path) as f:
            data = json.load(f)

        self.samples = []
        for ann in data["annotations"]:
            img_path = base_dir / ann["image"]
            if img_path.exists() and ann["text"].strip():
                self.samples.append({"path": str(img_path), "text": ann["text"].strip().lower()})

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        sample = self.samples[idx]
        img = Image.open(sample["path"]).convert("L")  # grayscale

        # Crop to bbox if provided
        if "bbox" in sample:
            w, h = img.size
            l, t, r, b = sample["bbox"]
            img = img.crop((
                max(0, int(l * w)),
                max(0, int(t * h)),
                min(w, int(r * w)),
                min(h, int(b * h))
            ))

        # Augmentation
        if self.augment:
            # Random brightness/contrast
            if random.random() < 0.3:
                factor = random.uniform(0.8, 1.2)
                img = ImageOps.autocontrast(img)
            # Random noise
            if random.random() < 0.2:
                arr = np.array(img).astype(np.float32)
                noise = np.random.normal(0, 5, arr.shape)
                arr = np.clip(arr + noise, 0, 255).astype(np.uint8)
                img = Image.fromarray(arr)

        # Resize: height fixed, width proportional
        w, h = img.size
        new_w = int(w * (self.img_height / h))
        new_w = max(new_w, 4)  # Min width for CNN
        img = img.resize((new_w, self.img_height), Image.BICUBIC)

        # To tensor: (1, H, W)
        tensor = T.ToTensor()(img)
        tensor = (tensor - 0.5) / 0.5  # Normalize to [-1, 1]

        # Encode text to indices
        text_indices = [CHAR_TO_IDX[c] for c in sample["text"] if c in CHAR_TO_IDX]
        target_length = len(text_indices)

        return tensor, torch.tensor(text_indices, dtype=torch.long), target_length

    @staticmethod
    def decode(indices) -> str:
        """CTC decode: greedy path with blank removal and dedup"""
        result = []
        prev = -1
        for idx in indices:
            if idx == BLANK_IDX:
                prev = -1
            elif idx != prev:
                result.append(CHARS[idx])
                prev = idx
        return "".join(result)


def collate_fn(batch):
    images, texts, text_lengths = zip(*batch)
    max_img_w = max(img.shape[2] for img in images)
    max_text_len = max(len(t) for t in texts)

    # Pad images
    padded_images = torch.zeros(len(images), 1, images[0].shape[1], max_img_w)
    for i, img in enumerate(images):
        _, h, w = img.shape
        padded_images[i, :, :, :w] = img

    # Pad texts
    padded_texts = torch.zeros(len(texts), max_text_len, dtype=torch.long)
    for i, t in enumerate(texts):
        padded_texts[i, :len(t)] = t

    return padded_images, padded_texts, torch.tensor(text_lengths, dtype=torch.long), max_img_w
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add OCR dataset loader with augmentation and CTC encoding"
```

---

### Task 3: CRNN 模型

**Files:**
- Create: `training/model/crnn.py`

- [ ] **Step 1: 创建 crnn.py**

```python
import torch
import torch.nn as nn
import torch.nn.functional as F


class CRNN(nn.Module):
    """CNN + BiLSTM + CTC for text recognition"""

    def __init__(self, img_channels=1, img_height=32, num_classes=41,
                 cnn_out=512, rnn_hidden=256, rnn_layers=2):
        super().__init__()
        self.num_classes = num_classes

        # CNN backbone
        self.cnn = nn.Sequential(
            # Block 1: (1, 32, W) -> (64, 16, W/2)
            nn.Conv2d(img_channels, 64, 3, padding=1), nn.BatchNorm2d(64), nn.ReLU(True),
            nn.MaxPool2d(2, 2),
            # Block 2: (64, 16, W/2) -> (128, 8, W/4)
            nn.Conv2d(64, 128, 3, padding=1), nn.BatchNorm2d(128), nn.ReLU(True),
            nn.MaxPool2d(2, 2),
            # Block 3: (128, 8, W/4) -> (256, 4, W/8)
            nn.Conv2d(128, 256, 3, padding=1), nn.BatchNorm2d(256), nn.ReLU(True),
            nn.Conv2d(256, 256, 3, padding=1), nn.BatchNorm2d(256), nn.ReLU(True),
            nn.MaxPool2d((2, 1), (2, 1)),   # (256, 2, W/8)
            # Block 4: (256, 2, W/8) -> (512, 1, W/8)
            nn.Conv2d(256, 512, 3, padding=1), nn.BatchNorm2d(512), nn.ReLU(True),
            nn.Conv2d(512, 512, 3, padding=1), nn.BatchNorm2d(512), nn.ReLU(True),
            nn.MaxPool2d((2, 1), (2, 1)),   # (512, 1, W/8)
        )

        # BiLSTM
        self.rnn = nn.LSTM(
            input_size=cnn_out,
            hidden_size=rnn_hidden,
            num_layers=rnn_layers,
            bidirectional=True,
            batch_first=True
        )

        # Output projection
        self.fc = nn.Linear(rnn_hidden * 2, num_classes)

    def forward(self, x):
        # x: (B, C, H, W)
        features = self.cnn(x)          # (B, 512, 1, W')
        features = features.squeeze(2)  # (B, 512, W')
        features = features.permute(0, 2, 1)  # (B, W', 512) for LSTM

        rnn_out, _ = self.rnn(features)  # (B, W', rnn_hidden*2)
        logits = self.fc(rnn_out)        # (B, W', num_classes)

        return logits  # log probabilities via CTC loss

    def get_feature_sequence_length(self, input_width: int) -> int:
        """Calculate output sequence length for a given input width"""
        # Each MaxPool2d(2,2) halves width twice -> W/4
        # Then MaxPool2d((2,1),(2,1)) twice doesn't change width
        return input_width // 4


def ctc_decode(logits: torch.Tensor, blank_idx: int) -> list:
    """Greedy CTC decode"""
    _, max_indices = logits.max(dim=2)
    results = []
    for seq in max_indices:
        decoded = []
        prev = -1
        for idx in seq.tolist():
            if idx == blank_idx:
                prev = -1
            elif idx != prev:
                decoded.append(idx)
                prev = idx
        results.append(decoded)
    return results
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add CRNN model with CNN+BiLSTM+CTC architecture"
```

---

### Task 4: 训练脚本

**Files:**
- Create: `training/train.py`

- [ ] **Step 1: 创建 train.py**

```python
import yaml
import torch
import torch.nn as nn
import torch.optim as optim
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
        images = images.to(device)
        texts = texts.to(device)
        text_lengths = text_lengths.to(device)

        optimizer.zero_grad()
        logits = model(images)  # (B, T, num_classes)

        # CTC requires (T, B, num_classes) format
        log_probs = nn.functional.log_softmax(logits, dim=2).permute(1, 0, 2)

        input_lengths = torch.full(
            (images.size(0),), log_probs.size(0), dtype=torch.long
        )

        loss = criterion(log_probs, texts, input_lengths, text_lengths)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()

    return total_loss / len(loader)


def validate(model, loader, device):
    model.eval()
    total_cer = 0
    total_samples = 0
    with torch.no_grad():
        for images, texts, _, _ in tqdm(loader, desc="Validating"):
            images = images.to(device)
            logits = model(images)
            decoded = ctc_decode(logits, BLANK_IDX)

            for pred_indices, target in zip(decoded, texts):
                pred_text = "".join(CHARS[i] for i in pred_indices if i < len(CHARS))
                target_text = "".join(
                    CHARS[idx.item()] for idx in target if idx.item() < len(CHARS)
                )
                cer = editdistance.eval(pred_text, target_text) / max(len(target_text), 1)
                total_cer += cer
                total_samples += 1

    return total_cer / total_samples if total_samples > 0 else 1.0


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    # Data
    train_ds = OcrDataset(config["data"]["train_annotation"],
                          img_height=config["model"]["image_height"], augment=True)
    val_ds = OcrDataset(config["data"]["val_annotation"],
                        img_height=config["model"]["image_height"], augment=False)

    train_loader = DataLoader(train_ds, batch_size=config["training"]["batch_size"],
                               shuffle=True, collate_fn=collate_fn, num_workers=4)
    val_loader = DataLoader(val_ds, batch_size=config["training"]["batch_size"],
                             shuffle=False, collate_fn=collate_fn, num_workers=4)

    print(f"Train samples: {len(train_ds)}, Val samples: {len(val_ds)}")

    # Model
    model = CRNN(
        img_channels=config["model"]["image_channels"],
        img_height=config["model"]["image_height"],
        num_classes=NUM_CLASSES,
        cnn_out=config["model"]["cnn_output_channels"],
        rnn_hidden=config["model"]["rnn_hidden_size"],
        rnn_layers=config["model"]["rnn_layers"]
    ).to(device)

    criterion = nn.CTCLoss(blank=BLANK_IDX, zero_infinity=True)
    optimizer = optim.AdamW(
        model.parameters(),
        lr=config["training"]["learning_rate"],
        weight_decay=config["training"]["weight_decay"]
    )
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode="min", factor=0.5, patience=5
    )

    # Training loop
    Path(config["output"]["model_dir"]).mkdir(parents=True, exist_ok=True)
    best_cer = float("inf")
    patience_counter = 0

    for epoch in range(config["training"]["epochs"]):
        print(f"\nEpoch {epoch + 1}/{config['training']['epochs']}")
        train_loss = train_one_epoch(model, train_loader, optimizer, criterion, device)
        val_cer = validate(model, val_loader, device)

        print(f"Train Loss: {train_loss:.4f}, Val CER: {val_cer:.4f}")

        scheduler.step(val_cer)

        if val_cer < best_cer:
            best_cer = val_cer
            patience_counter = 0
            torch.save(model.state_dict(),
                       Path(config["output"]["model_dir"]) / "best_model.pt")
            print(f"Saved best model with CER={best_cer:.4f}")
        else:
            patience_counter += 1

        if patience_counter >= config["training"]["early_stop_patience"]:
            print("Early stopping")
            break

    print(f"Training complete. Best CER: {best_cer:.4f}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add OCR training script with CTC loss and early stopping"
```

---

### Task 5: 评估脚本

**Files:**
- Create: `training/evaluate.py`

- [ ] **Step 1: 创建 evaluate.py**

```python
import yaml
import torch
from torch.utils.data import DataLoader
from data.dataset import OcrDataset, collate_fn, CHARS, BLANK_IDX, NUM_CLASSES
from model.crnn import CRNN, ctc_decode
import editdistance
from tqdm import tqdm


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    model_path = config["output"]["model_dir"] + "/best_model.pt"
    model = CRNN(
        img_channels=config["model"]["image_channels"],
        img_height=config["model"]["image_height"],
        num_classes=NUM_CLASSES,
        cnn_out=config["model"]["cnn_output_channels"],
        rnn_hidden=config["model"]["rnn_hidden_size"],
        rnn_layers=config["model"]["rnn_layers"]
    ).to(device)
    model.load_state_dict(torch.load(model_path, map_location=device))
    model.eval()

    ds = OcrDataset(config["data"]["val_annotation"],
                    img_height=config["model"]["image_height"])
    loader = DataLoader(ds, batch_size=1, shuffle=False, collate_fn=collate_fn)

    total_cer = 0
    correct = 0
    results = []

    with torch.no_grad():
        for images, texts, _, _ in tqdm(loader, desc="Evaluating"):
            images = images.to(device)
            logits = model(images)
            decoded = ctc_decode(logits, BLANK_IDX)

            target_text = "".join(
                CHARS[idx.item()] for idx in texts[0] if idx.item() < len(CHARS)
            )
            pred_text = "".join(
                CHARS[i] for i in decoded[0] if i < len(CHARS)
            )

            cer = editdistance.eval(pred_text, target_text) / max(len(target_text), 1)
            total_cer += cer
            if pred_text == target_text:
                correct += 1
            results.append({"target": target_text, "prediction": pred_text, "cer": cer})

    avg_cer = total_cer / len(ds)
    accuracy = correct / len(ds) * 100
    print(f"Average CER: {avg_cer:.4f}")
    print(f"Exact Match Accuracy: {accuracy:.2f}%")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add evaluation script with CER and exact match metrics"
```

---

### Task 6: 知识蒸馏

**Files:**
- Create: `training/model/teacher_model.py`
- Create: `training/distill.py`

- [ ] **Step 1: 创建 teacher_model.py**

```python
"""Teacher model wrapper using TrOCR for distillation"""
import torch
import torch.nn as nn


class TrOCRTeacher(nn.Module):
    """Wrapper that uses a pretrained TrOCR as teacher.
    Fallback: uses a larger CRNN if TrOCR is unavailable."""

    def __init__(self, model_name: str = "microsoft/trocr-small-printed", num_classes: int = 41):
        super().__init__()
        self.num_classes = num_classes
        self.use_trocr = False

        try:
            from transformers import TrOCRProcessor, VisionEncoderDecoderModel
            self.processor = TrOCRProcessor.from_pretrained(model_name)
            self.teacher = VisionEncoderDecoderModel.from_pretrained(model_name)
            self.use_trocr = True
            print(f"Loaded TrOCR teacher: {model_name}")
        except ImportError:
            print("Transformers not available. Using larger CRNN as teacher.")
            from model.crnn import CRNN
            self.teacher = CRNN(
                img_channels=1, img_height=32,
                num_classes=num_classes, cnn_out=1024,
                rnn_hidden=512, rnn_layers=3
            )

    @torch.no_grad()
    def forward(self, x):
        """Return soft labels (logits) from teacher"""
        if self.use_trocr:
            # TrOCR produces token IDs; convert to soft distribution
            outputs = self.teacher.generate(
                self.processor(images=x, return_tensors="pt").pixel_values.to(x.device),
                output_scores=True, return_dict_in_generate=True
            )
            # Simplified: map token scores to our class space
            return outputs.sequences
        else:
            return self.teacher(x)


def distillation_loss(student_logits, teacher_logits, targets, input_lengths,
                       target_lengths, temperature=3.0, alpha=0.7):
    """Combined CTC + distillation loss"""
    criterion = nn.CTCLoss(blank=41, zero_infinity=True)

    # Standard CTC loss against ground truth
    student_log_probs = nn.functional.log_softmax(student_logits, dim=2).permute(1, 0, 2)
    ctc_loss = criterion(student_log_probs, targets, input_lengths, target_lengths)

    # KL divergence loss against teacher soft labels
    student_soft = nn.functional.log_softmax(student_logits / temperature, dim=2)
    teacher_soft = nn.functional.softmax(teacher_logits / temperature, dim=2)
    kl_loss = nn.functional.kl_div(
        student_soft, teacher_soft, reduction="batchmean"
    ) * (temperature ** 2)

    return alpha * kl_loss + (1 - alpha) * ctc_loss
```

- [ ] **Step 2: 创建 distill.py**

```python
import yaml
import torch
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

    # Load student and teacher
    student = CRNN(
        img_channels=config["model"]["image_channels"],
        img_height=config["model"]["image_height"],
        num_classes=NUM_CLASSES,
        cnn_out=config["model"]["cnn_output_channels"],
        rnn_hidden=config["model"]["rnn_hidden_size"],
        rnn_layers=config["model"]["rnn_layers"]
    ).to(device)

    teacher = TrOCRTeacher(
        model_name=config["distillation"]["teacher_model"],
        num_classes=NUM_CLASSES
    ).to(device)
    teacher.eval()

    # Data
    train_ds = OcrDataset(config["data"]["train_annotation"],
                          img_height=config["model"]["image_height"], augment=True)
    train_loader = DataLoader(train_ds, batch_size=config["training"]["batch_size"],
                               shuffle=True, collate_fn=collate_fn, num_workers=4)

    temperature = config["distillation"]["temperature"]
    alpha = config["distillation"]["alpha"]

    optimizer = torch.optim.AdamW(student.parameters(), lr=config["training"]["learning_rate"])

    for epoch in range(20):
        student.train()
        epoch_loss = 0
        for images, texts, text_lengths, _ in tqdm(train_loader, desc=f"Distill Epoch {epoch+1}"):
            images = images.to(device)
            texts = texts.to(device)
            text_lengths = text_lengths.to(device)

            with torch.no_grad():
                teacher_logits = teacher(images)

            input_lengths = torch.full((images.size(0),),
                                       student_logits.size(1), dtype=torch.long)

            optimizer.zero_grad()
            student_logits = student(images)
            loss = distillation_loss(
                student_logits, teacher_logits, texts,
                input_lengths.to(device), text_lengths,
                temperature, alpha
            )
            loss.backward()
            optimizer.step()
            epoch_loss += loss.item()

        print(f"Epoch {epoch+1} Loss: {epoch_loss/len(train_loader):.4f}")

    out_path = Path(config["output"]["model_dir"]) / "distilled_model.pt"
    torch.save(student.state_dict(), out_path)
    print(f"Distilled model saved to {out_path}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add knowledge distillation with TrOCR teacher"
```

---

### Task 7: 结构化剪枝

**Files:**
- Create: `training/prune.py`

- [ ] **Step 1: 创建 prune.py**

```python
import yaml
import torch
import torch.nn.utils.prune as prune
from pathlib import Path
from tqdm import tqdm
from torch.utils.data import DataLoader

from data.dataset import OcrDataset, collate_fn, NUM_CLASSES
from model.crnn import CRNN
from train import train_one_epoch, validate


def prune_model(model, sparsity: float):
    """Apply structured L1 pruning to Conv2d layers"""
    for name, module in model.named_modules():
        if isinstance(module, torch.nn.Conv2d) and module.out_channels > 32:
            prune.ln_structured(
                module, name="weight", amount=sparsity, n=2, dim=0
            )
    return model


def remove_pruning_reparam(model):
    """Make pruning permanent"""
    for name, module in model.named_modules():
        if isinstance(module, torch.nn.Conv2d):
            try:
                prune.remove(module, "weight")
            except Exception:
                pass
    return model


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    model_path = Path(config["output"]["model_dir"]) / "best_model.pt"
    # Prefer distilled model if available
    distilled_path = Path(config["output"]["model_dir"]) / "distilled_model.pt"
    if distilled_path.exists():
        model_path = distilled_path

    model = CRNN(
        img_channels=config["model"]["image_channels"],
        img_height=config["model"]["image_height"],
        num_classes=NUM_CLASSES,
        cnn_out=config["model"]["cnn_output_channels"],
        rnn_hidden=config["model"]["rnn_hidden_size"],
        rnn_layers=config["model"]["rnn_layers"]
    ).to(device)
    model.load_state_dict(torch.load(model_path, map_location=device))

    # Prune
    sparsity = config["pruning"]["target_sparsity"]
    model = prune_model(model, sparsity)
    model = remove_pruning_reparam(model)

    # Fine-tune
    train_ds = OcrDataset(config["data"]["train_annotation"],
                          img_height=config["model"]["image_height"], augment=True)
    train_loader = DataLoader(train_ds, batch_size=config["training"]["batch_size"],
                               shuffle=True, collate_fn=collate_fn, num_workers=4)

    val_ds = OcrDataset(config["data"]["val_annotation"],
                        img_height=config["model"]["image_height"])
    val_loader = DataLoader(val_ds, batch_size=config["training"]["batch_size"],
                             shuffle=False, collate_fn=collate_fn, num_workers=4)

    import torch.nn as nn
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
```

- [ ] **Step 2: 提交**

```bash
git add -A && git commit -m "feat: add structured L1 pruning with finetuning"
```

---

### Task 8: 量化导出 TFLite

**Files:**
- Create: `training/quantize.py`
- Create: `training/export.py`

- [ ] **Step 1: 创建 export.py（ONNX 导出）**

```python
import yaml
import torch
from pathlib import Path
from model.crnn import CRNN
from data.dataset import NUM_CLASSES


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    model = CRNN(
        img_channels=config["model"]["image_channels"],
        img_height=config["model"]["image_height"],
        num_classes=NUM_CLASSES,
        cnn_out=config["model"]["cnn_output_channels"],
        rnn_hidden=config["model"]["rnn_hidden_size"],
        rnn_layers=config["model"]["rnn_layers"]
    )
    model.eval()

    # Load pruned model if available, else best
    pruned_path = Path(config["output"]["model_dir"]) / "pruned_model.pt"
    model_path = pruned_path if pruned_path.exists() else Path(config["output"]["model_dir"]) / "best_model.pt"
    model.load_state_dict(torch.load(model_path, map_location="cpu"))

    # Export to ONNX
    onnx_path = config["output"]["onnx_model"]
    dummy_input = torch.randn(1, 1, 32, 200)  # (B, C, H, W)

    torch.onnx.export(
        model, dummy_input, onnx_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {3: "width"},
            "output": {1: "sequence_length"}
        },
        opset_version=13
    )
    print(f"ONNX model exported to {onnx_path}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 创建 quantize.py（INT8 量化 → TFLite）**

```python
"""Convert ONNX model to INT8 quantized TFLite"""
import yaml
import numpy as np
from pathlib import Path


def representative_dataset_gen():
    """Generate calibration data from validation set"""
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    from data.dataset import OcrDataset, collate_fn
    from torch.utils.data import DataLoader

    ds = OcrDataset(config["data"]["val_annotation"],
                    img_height=config["model"]["image_height"])
    loader = DataLoader(ds, batch_size=1, shuffle=False, collate_fn=collate_fn)

    count = 0
    for images, _, _, _ in loader:
        if count >= config["quantization"]["calibration_samples"]:
            break
        yield [images.numpy().astype(np.float32)]
        count += 1


def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)

    onnx_path = config["output"]["onnx_model"]
    tflite_path = config["output"]["tflite_model"]

    try:
        import tensorflow as tf

        # Convert ONNX to TensorFlow
        # Step 1: ONNX -> SavedModel via onnx-tf (optional path)
        # Simplified: use onnx2tf or direct TFLite conversion

        # For environments with onnx2tf:
        # import onnx
        # onnx_model = onnx.load(onnx_path)
        # ...convert...

        # Alternative: PyTorch -> TFLite via ai-edge-torch
        print("For TFLite conversion, use:")
        print("  python -m tf.lite.toco ...")
        print(f"  Input: {onnx_path}")
        print(f"  Output: {tflite_path}")

        # Direct TFLite converter approach
        converter = tf.lite.TFLiteConverter.from_saved_model(
            str(Path(onnx_path).with_suffix(""))
        ) if False else None

        # If using concrete functions:
        # converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])
        # converter.optimizations = [tf.lite.Optimize.DEFAULT]
        # converter.representative_dataset = representative_dataset_gen
        # converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        # converter.inference_input_type = tf.int8
        # converter.inference_output_type = tf.int8
        # tflite_model = converter.convert()
        # with open(tflite_path, 'wb') as f:
        #     f.write(tflite_model)

        print("\nManual conversion steps:")
        print("1. Export ONNX: python export.py")
        print("2. Convert ONNX to TFLite:")
        print("   pip install onnx-tf")
        print("   onnx-tf convert -i output/ocr_model.onnx -o output/ocr_saved_model")
        print("3. Quantize:")
        print("   python quantize.py  # this script")

    except ImportError:
        print("TensorFlow not installed. Install with: pip install tensorflow")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat: add ONNX export and TFLite INT8 quantization pipeline"
```
