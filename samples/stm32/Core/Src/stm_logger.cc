// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020-2021 Intel Corporation. All Rights Reserved.

#include "Logger.h"
#include <cstdio>
#include <sstream>
#include <iomanip>
#include <memory>
#include <cstdarg> // for va_start
#include <cassert>


// if log level is right, vsprintf the args to buffer and log it
#define LOG_IT_(LEVEL)                                                                                                 \
    va_list args;                                                                                                      \
    va_start(args, format);                                                                                            \
    char buffer[LOG_BUFFER_SIZE];                                                                                      \
    auto Ok = vsprintf(buffer, format, args) >= 0;                                                    \
    if (!Ok)                                                                                                           \
        sprintf(buffer, "(bad printf format \"%s\")", format);                                        \
    printf("\n[%s|%s] %s", LEVEL, tag, buffer);                                                                         \
    va_end(args)

#define LOG_BUFFER_SIZE 512

namespace RealSenseID
{
Logger::Logger() {}
Logger::~Logger() {}
void Logger::SetCallback(LogCallback, RealSenseID::Logger::LogLevel, bool) {}

void Logger::Trace(const char* tag, const char* format, ...)
{
    LOG_IT_("T");
}

void Logger::Debug(const char* tag, const char* format, ...)
{
    LOG_IT_("D");
}

void Logger::Info(const char* tag, const char* format, ...)
{
    LOG_IT_("I");
}

void Logger::Warning(const char* tag, const char* format, ...)
{
    LOG_IT_("W");
}

void Logger::Error(const char* tag, const char* format, ...)
{
    LOG_IT_("E");
}

void Logger::Critical(const char* tag, const char* format, ...)
{
     LOG_IT_("C");
}

void Logger::DebugBytes(const char* tag, const char* msg, const char* buf, size_t size)
{
    std::stringstream ss;
    ss << std::hex << std::setw(2) << std::setfill('0');
    for (int i = 0; i < size; ++i)
        ss << (int)buf[i] << ' ';
    auto s = ss.str();
	printf("\n[%s|%s] %s %s", "DebugBytes", tag, msg, s.c_str());
}
}
