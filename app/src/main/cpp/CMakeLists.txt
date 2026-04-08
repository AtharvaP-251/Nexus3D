cmake_minimum_required(VERSION 3.22.1)

project("dsp_native")

# Add the dsp_stub library
add_library(
        dsp_native
        SHARED
        dsp_stub.cpp
)

# Find the standard Android log library
find_library(
        log-lib
        log
)

# Link everything together
target_link_libraries(
        dsp_native
        ${log-lib}
)
