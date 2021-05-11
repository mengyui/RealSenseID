// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020-2021 Intel Corporation. All Rights Reserved.
#include "McuSerial.h"
#include "CommonTypes.h"
#include "SerialPacket.h"
#include "Timer.h"
#include "Logger.h"

#include "usart.h"
#include "utils.h"
//#include "rtc.h"
static const char* LOG_TAG = "McuSerial";
  

namespace RealSenseID
{
namespace PacketManager
{
#if RSID_DEBUG_VALUES
static constexpr timeout_t recv_packet_timeout {20000};
#else
static constexpr timeout_t recv_packet_timeout {5000};
#endif
McuSerial::~McuSerial()
{
//    auto ignored_status = this->SendBytes(Commands::binmode0, strlen(Commands::binmode0));
//    (void)ignored_status;
//    ::close(_handle);
}

McuSerial::McuSerial(const SerialConfig& config)
{
    LOG_DEBUG(LOG_TAG, "Opening serial port %s @ %u baudrate", config.port, config.baudrate);
    
    //HAL_UART_Receive_IT(&huart1, &uart1_ch, 1);
    HAL_UART_Receive_IT(UART_F450_RX, &uart3_rx_buf, 1);
    //
    // send the "init 1\n"/"init 2\n"
    //auto* init_cmd = config.ser_type == SerialType::USB ? Commands::init_usb : Commands::init_host_uart;
    auto init_cmd = "init 1\n"; // TODO: hardcode to HOST UART -> "init 1\n"
    LOG_DEBUG(LOG_TAG, "init: %s", init_cmd);
    auto status = this->SendBytes(init_cmd, strlen(init_cmd));
    ::usleep(100000); // give time to device to enter the required mode

#ifndef RSID_SECURE
    // HACK: reset host pub keys
    // TODO remove this
    const char* resetKeys_usb = "\nresetHostPubKeys\n";
    status = this->SendBytes(resetKeys_usb, strlen(resetKeys_usb));
    if (status != SerialStatus::Ok)
    {
        LOG_ERROR(LOG_TAG, "Failed reset host pubkeys. status: %d", (int)status);
    }
#endif //RSID_SECURE
}


SerialStatus McuSerial::SendBytes(const char* buffer, size_t n_bytes)
{
    DEBUG_SERIAL(LOG_TAG, "[snd]", buffer, n_bytes);
#if 1
    uart3_last_begin = uart3_rx_index;
    HAL_UART_Transmit_IT(UART_F450_TX, (uint8_t *)buffer, n_bytes);
    while(UART_Transmit_State(UART_F450_TX) != HAL_UART_STATE_READY) ;
#else
    while(HAL_UART_GetState(&huart3) != HAL_UART_STATE_READY);
    HAL_UART_Transmit(&huart3, (uint8_t*)buffer, n_bytes, HAL_MAX_DELAY);
#endif
    return SerialStatus::Ok;
}

// receive all bytes and copy to the buffer or return error status
// timeout after recv_packet_timeout millis
SerialStatus McuSerial::RecvBytes(char* buffer, size_t n_bytes)
{
    uint16_t temp1,temp2;
    uint16_t i,j;
    if (n_bytes == 0)
    {
        LOG_ERROR(LOG_TAG, "Attempt to recv 0 bytes");
        return SerialStatus::RecvFailed;
    }
    //LOG_DEBUG(LOG_TAG, "McuSerial::RecvBytes %u %u %u", n_bytes, uart3_last_begin, uart3_rx_index);
#if 0
    if (HAL_OK == HAL_UART_Receive(&huart3, (uint8_t*)buffer, n_bytes, recv_packet_timeout.count()))
    {
        DEBUG_SERIAL(LOG_TAG, "[rcv]", buffer, n_bytes);
        return SerialStatus::Ok;
    }
    DEBUG_SERIAL(LOG_TAG, "[rcv]", buffer, n_bytes);
#else
		
    Timer timer {recv_packet_timeout};
    unsigned int total_bytes_read = 0;
    while (!timer.ReachedTimeout())
    {
        if ((uart3_last_begin + n_bytes) <= ((uart3_rx_index < uart3_last_begin)?(uart3_rx_index+USART_REC_LEN):uart3_rx_index))
        {
            for (int i = 0; i < n_bytes; ++i)
            {
                auto rx_idx = (uart3_last_begin + i) % USART_REC_LEN;
                buffer[i] = USART3_RX[rx_idx];
            }
            uart3_last_begin = (uart3_last_begin + n_bytes) % USART_REC_LEN;
            //LOG_DEBUG(LOG_TAG, "t: %u", uart3_last_begin);
            DEBUG_SERIAL(LOG_TAG, "[rcv]", buffer, n_bytes);
            return SerialStatus::Ok;
        }
        total_bytes_read = (uart3_rx_index + USART_REC_LEN - uart3_last_begin) % USART_REC_LEN;
        //LOG_DEBUG(LOG_TAG, "NOT READY l:%u r:%u %u", uart3_last_begin, uart3_rx_index, total_bytes_read);
    }
#endif
    LOG_DEBUG(LOG_TAG, "Timeout recv %zu bytes. Got only %zu bytes", n_bytes, total_bytes_read);
    return SerialStatus::RecvTimeout;
}

} // namespace PacketManager
} // namespace RealSenseID
