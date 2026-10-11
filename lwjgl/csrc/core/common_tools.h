/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
#pragma once

#include <stdlib.h>
#include <stdint.h>
#include <jni.h>

#if defined(__WIN32) || (defined(__APPLE__) && defined(__aarch64__))
    #define VA_LIST_CAST(param) (va_list)(uintptr_t)param##Address
#else
    #define VA_LIST_CAST(param) *(va_list *)(uintptr_t)param##Address
#endif

// -----------------------------------------------------

#ifdef __cplusplus
    #define EXTERN_C_ENTER extern "C" {
    #define EXTERN_C_EXIT }
#else
    #define EXTERN_C_ENTER
    #define EXTERN_C_EXIT
#endif

// -----------------------------------------------------

#define UNUSED_PARAM(param) \
    (void)(param);

#define UNUSED_PARAMS(a, b) \
    (void)(a); \
    (void)(b);

// -----------------------------------------------------

