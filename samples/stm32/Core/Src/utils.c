
#include "stm32f4xx_hal.h"
#include "utils.h"
#include "usart.h"

// USLEEP
int msleep(unsigned int milliseconds)
{
	HAL_Delay(milliseconds);
	return milliseconds;
}

#include <stdio.h>
// TIME
uint32_t timer_now()
{
	return HAL_GetTick();
}

// UART BUFFER
uint8_t uart3_rx_buf = 0;
uint8_t USART3_RX[USART_REC_LEN];

uint16_t uart3_rx_index = 0;
uint16_t uart3_last_begin = 0;

void HAL_UART_TxCpltCallback(UART_HandleTypeDef *huart)
{
}

void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
{
    if (huart == UART_F450_RX)
    {
        USART3_RX[uart3_rx_index] = uart3_rx_buf;
        uart3_rx_index++;
        if(uart3_rx_index >= USART_REC_LEN)
            uart3_rx_index = 0;
        HAL_UART_Receive_IT(UART_F450_RX, &uart3_rx_buf, 1);
    }
}
