import torch
import torch.nn as nn

class CRNN(nn.Module):
    def __init__(self, img_channels=1, img_height=32, num_classes=41, cnn_out=512, rnn_hidden=256, rnn_layers=2):
        super().__init__()
        self.num_classes = num_classes
        self.cnn = nn.Sequential(
            nn.Conv2d(img_channels, 64, 3, padding=1), nn.BatchNorm2d(64), nn.ReLU(True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(64, 128, 3, padding=1), nn.BatchNorm2d(128), nn.ReLU(True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(128, 256, 3, padding=1), nn.BatchNorm2d(256), nn.ReLU(True),
            nn.Conv2d(256, 256, 3, padding=1), nn.BatchNorm2d(256), nn.ReLU(True),
            nn.MaxPool2d((2, 1), (2, 1)),
            nn.Conv2d(256, 512, 3, padding=1), nn.BatchNorm2d(512), nn.ReLU(True),
            nn.Conv2d(512, 512, 3, padding=1), nn.BatchNorm2d(512), nn.ReLU(True),
            nn.MaxPool2d((2, 1), (2, 1)),
        )
        self.rnn = nn.LSTM(input_size=cnn_out, hidden_size=rnn_hidden, num_layers=rnn_layers, bidirectional=True, batch_first=True)
        self.fc = nn.Linear(rnn_hidden * 2, num_classes)

    def forward(self, x):
        features = self.cnn(x)
        features = features.squeeze(2)
        features = features.permute(0, 2, 1)
        rnn_out, _ = self.rnn(features)
        logits = self.fc(rnn_out)
        return logits

def ctc_decode(logits, blank_idx):
    _, max_indices = logits.max(dim=2)
    results = []
    for seq in max_indices:
        decoded, prev = [], -1
        for idx in seq.tolist():
            if idx == blank_idx:
                prev = -1
            elif idx != prev:
                decoded.append(idx)
                prev = idx
        results.append(decoded)
    return results
