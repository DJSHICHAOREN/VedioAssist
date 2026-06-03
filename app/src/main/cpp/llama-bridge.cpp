#include <jni.h>
#include <string>
#include <vector>
#include "llama.h"

struct LlamaContext {
    llama_model* model;
    llama_context* ctx;
    const llama_vocab* vocab;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_movieenglish_assistant_llama_LlamaEngine_nativeInit(
    JNIEnv* env, jobject, jstring modelPath, jint nThreads
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_backend_init();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 99;

    llama_model* model = llama_model_load_from_file(path, modelParams);
    if (!model) {
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = 2048;
    ctxParams.n_threads = nThreads;

    llama_context* ctx = llama_new_context_with_model(model, ctxParams);
    if (!ctx) {
        llama_model_free(model);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    auto* lc = new LlamaContext{model, ctx, llama_model_get_vocab(model)};
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(lc);
}

JNIEXPORT jstring JNICALL
Java_com_movieenglish_assistant_llama_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject, jlong ptr, jstring prompt, jint maxTokens
) {
    auto* lc = reinterpret_cast<LlamaContext*>(ptr);
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    int nTokens = -llama_tokenize(lc->vocab, promptStr, strlen(promptStr), nullptr, 0, true, true);
    std::vector<llama_token> tokens(nTokens);
    llama_tokenize(lc->vocab, promptStr, strlen(promptStr), tokens.data(), nTokens, true, true);

    std::string result;
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());

    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(lc->ctx, batch) != 0) break;

        float* logits = llama_get_logits_ith(lc->ctx, batch.n_tokens - 1);
        int nextToken = 0;
        float maxLogit = logits[0];
        int vocabSize = llama_n_vocab(lc->vocab);
        for (int j = 1; j < vocabSize; j++) {
            if (logits[j] > maxLogit) {
                maxLogit = logits[j];
                nextToken = j;
            }
        }

        if (llama_vocab_is_eog(lc->vocab, nextToken)) break;

        char buf[256];
        int len = llama_token_to_piece(lc->vocab, nextToken, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }

        batch = llama_batch_get_one(&nextToken, 1);
    }

    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_movieenglish_assistant_llama_LlamaEngine_nativeFree(
    JNIEnv*, jobject, jlong ptr
) {
    auto* lc = reinterpret_cast<LlamaContext*>(ptr);
    llama_free(lc->ctx);
    llama_model_free(lc->model);
    llama_backend_free();
    delete lc;
}

} // extern "C"
