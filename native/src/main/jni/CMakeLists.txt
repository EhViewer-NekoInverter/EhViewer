project(ehviewer)
cmake_minimum_required(VERSION 3.4.1)

add_subdirectory(a7zip)
add_subdirectory(image)

add_library(${PROJECT_NAME} SHARED main.c)

target_include_directories(${PROJECT_NAME} PUBLIC a7zip/a7zip image/image)

target_link_libraries(${PROJECT_NAME} a7zip image)