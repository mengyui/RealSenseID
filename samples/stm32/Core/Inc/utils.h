#ifndef __UTILS_H__
#define __UTILS_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// USLEEP
#define usleep(microseconds) msleep(microseconds / 1000)
int msleep(unsigned int milliseconds);

// TIME
extern uint32_t timer_now();

// UART BUFFER
#define UART_TTY     (&huart1)
#define UART_F450_TX (&huart3)
#define UART_F450_RX (&huart3)
	
#define UART_Transmit_State(huart) (huart->gState)
#define UART_Receive_State(huart)  (huart->RxState)

extern uint8_t uart3_rx_buf;
#define USART_REC_LEN       1024
extern uint8_t USART3_RX[USART_REC_LEN];

extern uint16_t uart3_rx_index;
extern uint16_t uart3_last_begin;

#ifdef __cplusplus
}
#endif

#endif
