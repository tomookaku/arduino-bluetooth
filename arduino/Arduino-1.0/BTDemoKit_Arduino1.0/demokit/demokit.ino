#include <Wire.h>
#include <Servo.h>

#include <SoftwareSerial.h>
#include <SBSBT001.h>

#define  LED1_RED       5
#define  LED1_GREEN     4
#define  LED1_BLUE      3

#define  LIGHT_SENSOR   A3
#define  TEMP_SENSOR    A4

#define  BUTTON1        10
#define  BUTTON2        11
#define  BUTTON3        12

#define  BUZZER         2

SBSBT001 sbsbt001("surfgrid.org",
	          "DemoKit",
		  "DemoKit Arduino Board",
		  "1.0",
		  "http://surfgrid.org",
		  "0000000012345678");

Servo servos[3];

void setup();
void loop();

void init_buttons()
{
	pinMode(BUTTON1, INPUT);
	pinMode(BUTTON2, INPUT);
	pinMode(BUTTON3, INPUT);

	// enable the internal pullups
	digitalWrite(BUTTON1, HIGH);
	digitalWrite(BUTTON2, HIGH);
	digitalWrite(BUTTON3, HIGH);
}

void init_leds()
{
	pinMode(LED1_RED, OUTPUT);
	pinMode(LED1_GREEN, OUTPUT);
	pinMode(LED1_BLUE, OUTPUT);

	analogWrite(LED1_RED, 255);
	analogWrite(LED1_GREEN, 255);
	analogWrite(LED1_BLUE, 255);
}


byte b1, b2, b3, c;

void setup()
{
	Serial.begin(115200);
	Serial.print("\r\nStart\r\n");

	init_leds();
	init_buttons();

        pinMode(BUZZER, OUTPUT);

	b1 = digitalRead(BUTTON1);
	b2 = digitalRead(BUTTON2);

	c = 0;

	sbsbt001.begin();
}

void loop()
{
	byte err;
	byte idle;
	static byte count = 0;
	byte msg[3];

	if (sbsbt001.isConnected()) {
		int len = sbsbt001.read(msg, sizeof(msg));
		int i;
		byte b;
		uint16_t val;
		int x, y;
		char c0;

		if (len > 0) {
                        Serial.println("-------------");
                        Serial.println(msg[0], HEX);
                        Serial.println(msg[1], HEX);
                        Serial.println(msg[2], HEX);

			// assumes only one command per packet
			if (msg[0] == 0x2) {
				if (msg[1] == 0x0) {
                                        Serial.println("LED1_RED");
					analogWrite(LED1_RED, msg[2]);
                                }
				else if (msg[1] == 0x1) {
                                        Serial.println("LED1_GREEN");
					analogWrite(LED1_GREEN, msg[2]);
				}
                                else if (msg[1] == 0x2) {
                                        Serial.println("LED1_BLUE");
					analogWrite(LED1_BLUE, msg[2]);
				}
			} else if (msg[0] == 0x3) {
				if (msg[1] == 0x0) {
                                        Serial.println("RELAY1");
                                        for(int x = 0; x < 100; x++)
                                        {
                                                digitalWrite(BUZZER, HIGH);
                                                delay(4);
                                                digitalWrite(BUZZER, LOW);
                                                delay(4);
                                        }
				}
			}
		}

		msg[0] = 0x1;

		b = digitalRead(BUTTON1);
		if (b != b1) {
                        Serial.println("BUTTON1: " + String(b, DEC));
			msg[1] = 0;
			msg[2] = b ? 0 : 1;
			sbsbt001.write(msg, sizeof(msg));
			b1 = b;
		}

		b = digitalRead(BUTTON2);
		if (b != b2) {
                        Serial.println("BUTTON2");
			msg[1] = 1;
			msg[2] = b ? 0 : 1;
			sbsbt001.write(msg, sizeof(msg));
			b2 = b;
		}

		b = digitalRead(BUTTON3);
		if (b != b3) {
                        Serial.println("BUTTON3");
			msg[1] = 2;
			msg[2] = b ? 0 : 1;
			sbsbt001.write(msg, sizeof(msg));
			b3 = b;
		}

		switch (count++ % 0x10) {
		case 0:
                        val = analogRead(TEMP_SENSOR);
			msg[0] = 0x4;
			msg[1] = val >> 8;
			msg[2] = val & 0xff;
			sbsbt001.write(msg, sizeof(msg));
			break;

		case 0x4:
			val = analogRead(LIGHT_SENSOR);
			msg[0] = 0x5;
			msg[1] = val >> 8;
			msg[2] = val & 0xff;
			sbsbt001.write(msg, sizeof(msg));
			break;
		}
	} else {
		analogWrite(LED1_RED, 0);
		analogWrite(LED1_GREEN, 0);
		analogWrite(LED1_BLUE, 0);
	}

	delay(10);
}

