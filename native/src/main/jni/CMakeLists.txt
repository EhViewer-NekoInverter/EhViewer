project(ehviewer)
cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Oz -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections -flto\
           -mllvm -polly \
           -mllvm -polly-run-dce \
		   -mllvm -polly-run-inliner \
           -mllvm -polly-isl-arg=--no-schedule-serialize-sccs \
		   -mllvm -polly-ast-use-context \
           -mllvm -polly-detect-keep-going \
           -mllvm -polly-position=before-vectorizer \
		   -mllvm -polly-vectorizer=stripmine \
           -mllvm -polly-detect-profitability-min-per-loop-insts=40 \
		   -mllvm -polly-invariant-load-hoisting")

# Build a7zip
add_subdirectory(a7zip)

# Build hippo's image lib
add_subdirectory(image)

# Build and link our app's native lib
add_library(${PROJECT_NAME} SHARED main.c)
target_include_directories(${PROJECT_NAME} PUBLIC a7zip/a7zip image/image)
target_link_libraries(${PROJECT_NAME} a7zip image -Wl,--gc-sections)