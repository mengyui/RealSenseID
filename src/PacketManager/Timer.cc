// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020-2021 Intel Corporation. All Rights Reserved.

#include "Timer.h"
#ifdef STM32_HAL
#include "utils.h"
#include <cstdio>
#endif

namespace RealSenseID
{
namespace PacketManager
{

Timer::Timer(timeout_t threshold) : _timeout {threshold}
#ifndef STM32_HAL
, _start_tp {clock::now()}
#else
, _start_tp {timer_now()}
#endif
{
}

Timer::Timer()
#ifndef STM32_HAL
 : Timer {timeout_t::max()}
#else
 : Timer {UINT_MAX}
#endif
{
}

timeout_t Timer::Elapsed() const
{
#ifndef STM32_HAL
    return std::chrono::duration_cast<timeout_t>(clock::now() - _start_tp);
#else
    return timeout_t{timer_now() - _start_tp};
#endif
}

timeout_t Timer::TimeLeft() const
{
#ifdef STM32_HAL
	if (_timeout <= Elapsed()) return 0;
#endif
    return _timeout - Elapsed();
}

bool Timer::ReachedTimeout() const
{
    return TimeLeft() <= timeout_t {0};
}

void Timer::Reset()
{
#ifndef STM32_HAL
    _start_tp = clock ::now();
#else
    _start_tp = timer_now();
#endif
}
} // namespace PacketManager
} // namespace RealSenseID
