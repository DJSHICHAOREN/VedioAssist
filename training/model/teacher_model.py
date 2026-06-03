import torch, torch.nn as nn

class TrOCRTeacher(nn.Module):
    def __init__(self, model_name="microsoft/trocr-small-printed", num_classes=41):
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
            self.teacher = CRNN(img_channels=1, img_height=32, num_classes=num_classes, cnn_out=1024, rnn_hidden=512, rnn_layers=3)

    @torch.no_grad()
    def forward(self, x):
        if self.use_trocr:
            return torch.zeros(x.size(0), 64, self.num_classes)  # placeholder
        else:
            return self.teacher(x)

def distillation_loss(student_logits, teacher_logits, targets, input_lengths, target_lengths, temperature=3.0, alpha=0.7):
    criterion = nn.CTCLoss(blank=41, zero_infinity=True)
    student_log_probs = nn.functional.log_softmax(student_logits, dim=2).permute(1, 0, 2)
    ctc_loss = criterion(student_log_probs, targets, input_lengths, target_lengths)
    student_soft = nn.functional.log_softmax(student_logits / temperature, dim=2)
    teacher_soft = nn.functional.softmax(teacher_logits / temperature, dim=2)
    kl_loss = nn.functional.kl_div(student_soft, teacher_soft, reduction="batchmean") * (temperature ** 2)
    return alpha * kl_loss + (1 - alpha) * ctc_loss
