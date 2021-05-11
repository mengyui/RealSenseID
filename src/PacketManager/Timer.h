// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020-2021 Intel Corporation. All Rights Reserved.

#pragma once

// timeout helper to help keep track of timeouts.
// uses std::chrono::steady_clock

#include "CommonTypes.h"
#include <chrono>

namespace RealSenseID
{
namespace PacketManager
{
class Timer
{
public:
#ifndef STM32_HAL
    using clock = std::chrono::steady_clock;
#else
    using clock = std::chrono::system_clock;
#endif
    Timer();
    explicit Timer(timeout_t timeout);
    
    timeout_t Elapsed() const;
    timeout_t TimeLeft() const;
    bool ReachedTimeout() const;
    void Reset();

private:
    timeout_t _timeout;
#ifndef STM32_HAL
    std::chrono::time_point<clock> _start_tp;
#else
    uint32_t _start_tp;
#endif
};
} // namespace PacketManager
} // namespace RealSenseID
