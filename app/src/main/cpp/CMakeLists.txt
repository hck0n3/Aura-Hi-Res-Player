# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("superpowered-bridge")

# Set paths for Superpowered SDK (Assumes it's placed in superpowered_sdk folder relative to cpp)
set(SUPERPOWERED_DIR ${CMAKE_CURRENT_SOURCE_DIR}/superpowered_sdk)

# Include directories
include_directories(
    ${SUPERPOWERED_DIR}
)

# Add our JNI source file
add_library(
    superpowered-bridge
    SHARED
    superpowered/SuperpoweredBridge.cpp
)

# We might not have the static libraries downloaded yet. Let's create dummy targets or link conditionally, 
# or just assume the user will place libSuperpoweredAndroid.a in the appropriate ABI folders.
# To not break the build if the folder isn't there yet, we'll conditionally include it.

if(EXISTS "${SUPERPOWERED_DIR}/libSuperpoweredAndroid${ANDROID_ABI}.a")
    add_library(superpowered STATIC IMPORTED)
    set_target_properties(superpowered PROPERTIES IMPORTED_LOCATION ${SUPERPOWERED_DIR}/libSuperpoweredAndroid${ANDROID_ABI}.a)
    target_link_libraries(superpowered-bridge superpowered log)
else()
    # Just link log for now, so it compiles
    target_link_libraries(superpowered-bridge log)
endif()

