// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020-2021 Intel Corporation. All Rights Reserved.

#pragma once

#include <chrono>

namespace RealSenseID
{
namespace CommonValues
{
#ifndef STM32_HAL
#if RSID_DEBUG_VALUES
constexpr std::chrono::milliseconds enroll_max_timeout {120000};
constexpr std::chrono::milliseconds auth_max_timeout {120000};
#else
constexpr std::chrono::milliseconds enroll_max_timeout {60000};
constexpr std::chrono::milliseconds auth_max_timeout {10000};
#endif
#else // STM32_HAL
#if RSID_DEBUG_VALUES
constexpr uint32_t enroll_max_timeout {120000};
constexpr uint32_t auth_max_timeout {120000};
#else
constexpr uint32_t enroll_max_timeout {60000};
constexpr uint32_t auth_max_timeout {10000};
#endif
#endif // STM32_HAL
} // namespace Values
} // namespace RealSenseID