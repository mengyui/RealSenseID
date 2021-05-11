#include <stdio.h>
#include "usart.h"

#define UART_TTY &huart1

#define PUTCHAR_PROTOTYPE int fputc(int ch, FILE *f)
#define GETCHAR_PROTOTYPE int fgetc(FILE *f)
PUTCHAR_PROTOTYPE
{
	/* Place your implementation of fputc here */
  /* e.g. write a character to the EVAL_COM1 and Loop until the end of transmission */
	if (ch == '\n')
		HAL_UART_Transmit(UART_TTY, (uint8_t *)"\r\n", 2, HAL_MAX_DELAY);
	else
		HAL_UART_Transmit(UART_TTY, (uint8_t *)&ch, 1, HAL_MAX_DELAY);

	return ch;
}
GETCHAR_PROTOTYPE
{
	while(HAL_UART_GetState(UART_TTY) != HAL_UART_STATE_READY);
	
	uint8_t rx_buf = 0;
	HAL_UART_Receive(UART_TTY, &rx_buf, 1, HAL_MAX_DELAY);

	fputc(rx_buf, f);	
	return rx_buf;
}
