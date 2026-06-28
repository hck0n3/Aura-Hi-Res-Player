# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("superpowered-bridge")

# Set paths for Superpowered SDK (Assumes it's placed in superpowered/ folder relative to cpp)
set(SUPERPOWERED_DIR ${CMAKE_CURRENT_SOURCE_DIR}/superpowered)

# Include directories
include_directories(
    ${SUPERPOWERED_DIR}/include
)

# Add our JNI source file
add_library(
    superpowered-bridge
    SHARED
    superpowered/SuperpoweredBridge.cpp
)

# Add the static Superpowered library
add_library(superpowered STATIC IMPORTED)
set_target_properties(superpowered PROPERTIES IMPORTED_LOCATION ${SUPERPOWERED_DIR}/lib/${ANDROID_ABI}/libSuperpoweredAndroid.a)

# Link the libraries
target_link_libraries(superpowered-bridge superpowered log)
