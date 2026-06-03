"""Compare model quality before and after pruning/quantization"""
import yaml, torch, json
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer
from tqdm import tqdm

def evaluate_model(model, tokenizer, prompts, device):
    model.eval(); results = []
    for prompt in tqdm(prompts, desc="Evaluating"):
        inputs = tokenizer(prompt, return_tensors="pt").to(device)
        with torch.no_grad():
            outputs = model.generate(**inputs, max_new_tokens=128, do_sample=False, temperature=0.7, pad_token_id=tokenizer.eos_token_id)
            response = tokenizer.decode(outputs[0], skip_special_tokens=True)
            lm_out = model(**inputs, labels=inputs["input_ids"])
            ppl = torch.exp(torch.tensor(lm_out.loss.item())).item()
        results.append({"prompt": prompt, "response": response[len(prompt):], "perplexity": ppl})
    return results

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    original_path = Path(config["model"]["local_path"])
    optimized_path = Path(config["model"]["output_path"])
    if not optimized_path.exists(): optimized_path = original_path
    tokenizer = AutoTokenizer.from_pretrained(str(original_path), trust_remote_code=True)
    prompts = config["evaluation"]["test_prompts"]
    print("=== Evaluating Original Model ===")
    orig_model = AutoModelForCausalLM.from_pretrained(str(original_path), torch_dtype=torch.float16, trust_remote_code=True, device_map="auto")
    orig_results = evaluate_model(orig_model, tokenizer, prompts, device)
    del orig_model; torch.cuda.empty_cache()
    print("\n=== Evaluating Optimized Model ===")
    opt_model = AutoModelForCausalLM.from_pretrained(str(optimized_path), torch_dtype=torch.float16, trust_remote_code=True, device_map="auto")
    opt_results = evaluate_model(opt_model, tokenizer, prompts, device)
    del opt_model
    print("\n=== Comparison ===")
    for i, prompt in enumerate(prompts):
        print(f"\nPrompt: {prompt}")
        print(f"  Original:   {orig_results[i]['response']}")
        print(f"  Optimized:  {opt_results[i]['response']}")
        print(f"  PPL delta:  {opt_results[i]['perplexity'] - orig_results[i]['perplexity']:+.2f}")
    output_dir = Path(config["evaluation"]["comparison_output"])
    output_dir.mkdir(parents=True, exist_ok=True)
    with open(output_dir / "comparison.json", "w") as f:
        json.dump({"original": orig_results, "optimized": opt_results}, f, indent=2, ensure_ascii=False)
    print(f"\nResults saved to {output_dir / 'comparison.json'}")

if __name__ == "__main__":
    main()
