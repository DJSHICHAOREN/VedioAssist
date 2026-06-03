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
                self.samples.append({"path": str(img_path), "text": ann["text"].strip().lower(), "bbox": ann.get("bbox")})

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        sample = self.samples[idx]
        img = Image.open(sample["path"]).convert("L")
        if "bbox" in sample and sample["bbox"]:
            w, h = img.size
            l, t, r, b = sample["bbox"]
            img = img.crop((max(0,int(l*w)), max(0,int(t*h)), min(w,int(r*w)), min(h,int(b*h))))
        if self.augment:
            if random.random() < 0.3:
                img = ImageOps.autocontrast(img)
            if random.random() < 0.2:
                arr = np.array(img).astype(np.float32)
                noise = np.random.normal(0, 5, arr.shape)
                arr = np.clip(arr + noise, 0, 255).astype(np.uint8)
                img = Image.fromarray(arr)
        w, h = img.size
        new_w = max(int(w * (self.img_height / h)), 4)
        img = img.resize((new_w, self.img_height), Image.BICUBIC)
        tensor = T.ToTensor()(img)
        tensor = (tensor - 0.5) / 0.5
        text_indices = [CHAR_TO_IDX[c] for c in sample["text"] if c in CHAR_TO_IDX]
        target_length = len(text_indices)
        return tensor, torch.tensor(text_indices, dtype=torch.long), target_length

    @staticmethod
    def decode(indices) -> str:
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
    padded_images = torch.zeros(len(images), 1, images[0].shape[1], max_img_w)
    for i, img in enumerate(images):
        _, h, w = img.shape
        padded_images[i, :, :, :w] = img
    padded_texts = torch.zeros(len(texts), max_text_len, dtype=torch.long)
    for i, t in enumerate(texts):
        padded_texts[i, :len(t)] = t
    return padded_images, padded_texts, torch.tensor(text_lengths, dtype=torch.long), max_img_w
