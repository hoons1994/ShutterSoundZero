cmake_minimum_required(VERSION 3.22.1)
project(spake2 LANGUAGES C CXX)

add_library(spake2 SHARED spake2-c/sha512.c spake2-c/spake2.c spake2-c/secure_random.c spake2_jni.cpp)
target_include_directories(spake2 PRIVATE spake2-c/include)
target_compile_features(spake2 PRIVATE cxx_std_17)
target_compile_options(spake2 PRIVATE -Werror=format -fvisibility=hidden)
target_link_options(spake2 PRIVATE "-Wl,-z,max-page-size=16384")
