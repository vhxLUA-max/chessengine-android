cmake_minimum_required(VERSION 3.22.1)

project(cheeziengine)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

file(GLOB STOCKFISH_SOURCES CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/engine/stockfish/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/engine/stockfish/src/nnue/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/engine/stockfish/src/nnue/features/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/engine/stockfish/src/syzygy/*.cpp"
)

list(REMOVE_ITEM STOCKFISH_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/engine/stockfish/src/main.cpp"
)

add_library(cheeziengine SHARED native_engine.cpp ${STOCKFISH_SOURCES})

target_include_directories(cheeziengine PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/engine/stockfish/src")

target_compile_definitions(cheeziengine PRIVATE NDEBUG NNUE_EMBEDDING_OFF)

target_compile_options(cheeziengine PRIVATE -O3 -ffunction-sections -fdata-sections)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(cheeziengine PRIVATE USE_NEON=8 USE_POPCNT IS_64BIT)
    target_compile_options(cheeziengine PRIVATE -march=armv8-a)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_definitions(cheeziengine PRIVATE USE_NEON=7)
    target_compile_options(cheeziengine PRIVATE -march=armv7-a -mfpu=neon -mfloat-abi=softfp)
endif()

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(cheeziengine ${log-lib} ${android-lib})
target_link_options(cheeziengine PRIVATE -Wl,--gc-sections)
