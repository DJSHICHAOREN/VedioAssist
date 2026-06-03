cmake_minimum_required(VERSION 3.18)
project("llama-bridge")

add_library(llama-bridge SHARED llama-bridge.cpp)
find_library(log-lib log)

add_library(llama STATIC IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.a)

target_link_libraries(llama-bridge llama ${log-lib})
