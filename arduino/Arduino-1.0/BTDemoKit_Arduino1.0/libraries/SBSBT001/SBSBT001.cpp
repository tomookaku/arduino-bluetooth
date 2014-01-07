#include "SBSBT001.h"

SoftwareSerial softSerial(BT_RX, BT_TX);

SBSBT001::SBSBT001(const char *manufacturer,
                   const char *model,
                   const char *description,
                   const char *version,
                   const char *uri,
                   const char *serial) : manufacturer(manufacturer),
                                         model(model),
                                         description(description),
                                         version(version),
                                         uri(uri),
                                         serial(serial),
                                         connected(false)
{
	pairing = false;
	response = ""; 
	btaddress = "";

	msg_length = 0;
	msg_length2 = 0;

	mySerial = &softSerial;
}

void SBSBT001::begin(void)
{
  pinMode(BT_SW, INPUT);
  pinMode(BT_JUMPER, INPUT);

  mySerial->begin(BT_BAUD);

  delay(100);

  Serial.println("begin start...");

  Serial.println("AT");
  mySerial->print("AT\r");
  readline(false);

  Serial.println("ATZ");
  mySerial->print("ATZ\r");
  readline(false);

  delay(100);

  Serial.println("AT+BTNAME=SBSBT001");
  mySerial->print("AT+BTNAME=SBSBT001\r");
  readline(false);

  if (digitalRead(BT_JUMPER) == LOW) {
    Serial.println("AT+BTSEC,0,0");
    mySerial->print("AT+BTSEC,0,0\r");
    readline(false);

    Serial.println("AT+BTSCAN,3,0");
    mySerial->print("AT+BTSCAN,3,0\r");
  }
  else {
    Serial.println("AT+BTSEC,1,1");
    mySerial->print("AT+BTSEC,1,1\r");
    readline(false);

    Serial.println("AT+BTKEY=1234");
    mySerial->print("AT+BTKEY=1234\r");
    readline(false);

    Serial.println("AT+BTSCAN,2,0");
    mySerial->print("AT+BTSCAN,2,0\r");
  }
  readline(false);

  connected = false;

  response = "";
  msg_length = 0;
  msg_length2 = 0;

  Serial.println("begin end.");
}

bool SBSBT001::isConnected(void) 
{
  btConnectionCheck();
  btPairingCheck();
  return connected;
}

bool SBSBT001::isConnected2(void) 
{
  btConnectionCheck();
  return connected;
}

int SBSBT001::read(byte *buff, int len)
{
  if (!connected) {
    response = "";
    msg_length = 0;
    msg_length2 = 0;
    return 0;
  }

  int n = msg_length;
  if (n == 0)
  	return 0;

  if (n > len)
  	n = len;

  for (int i = 0; i < n; i++)
    buff[i] = msg[i];

  response = "";
  msg_length = 0;
  msg_length2 = 0;
  return n;
}

int SBSBT001::write(byte *buff, int len)
{
  if (!connected)
    return 0;

  for (int i = 0; i < len; i++) {
    mySerial->write(buff[i]);
  }

  mySerial->flush();
  return len;
}

void SBSBT001::btConnectionCheck(void)
{
  char c;

  if (mySerial->available()) {
    while((c = mySerial->read()) != -1) {
      if (!connected) {
        Serial.write(c);

        if (response.length() == 0) {
          if (c == 'C') {
            // CONNECT ?
            response = String(c);
          }
        }
        else {
          response += String(c);
          if (response.length() == 22) {
            if (response.startsWith("CONNECT")) {
              response = "";
              msg_length = 0;
              msg_length2 = 0;
              Serial.println("connected: true");
          	  connected = true;
              break;
            }
          }
          else if (response.length() > 22) {
              response = "";
              msg_length = 0;
              msg_length2 = 0;
          }
        }
      }
      else { // connected
        msg[msg_length2++] = c;

        if (response.length() == 0) {
          if (c == 'D') {
            // DISCONNECT ?
            response = String(c);
          }
          else {
            msg_length = msg_length2;
          }
        }
        else {
          response += String(c);
          if (response.length() < 12) {
            if (!response.equals(String("DISCONNECT\r\n").substring(0, response.length()))) {
              response = "";
              msg_length = msg_length2;
            }
          }
          else if (response.length() == 12) {
            delay(500);

            Serial.println("connected: false");
            connected = false;

            if (digitalRead(BT_JUMPER) == HIGH) {
              Serial.println("AT+BTCANCEL");
              mySerial->print("AT+BTCANCEL\r");
              readline(false);

              Serial.println("AT+BTSCAN,2,0");
              mySerial->print("AT+BTSCAN,2,0\r");
              readline(false);
            }

            response = "";
            msg_length = 0;
	        msg_length2 = 0;
          }
        }
      }
    }
  }
}

void SBSBT001::btPairingCheck(void)
{
  if (digitalRead(BT_SW) == LOW) {
    pairing = true;

    for (int i = 0; i < 10; i++) {
        delay(100);
        if (digitalRead(BT_SW) == HIGH) {
          pairing = false;
          break;
        }
    }

    if (pairing) {
      pairing = false;

      Serial.println("Pairing SW ON");

      Serial.println("AT+BTCANCEL");
      mySerial->print("AT+BTCANCEL\r");
      readline(false);

      while (digitalRead(BT_SW) == LOW);

      Serial.println("AT+BTSCAN,3,0");
      mySerial->print("AT+BTSCAN,3,0\r");
      readline(false);
    }
  }
}

void SBSBT001::readline(bool b)
{
  mySerial->flush();

  while (!mySerial->available()) {
    delay(100);
  }

  String ans = "";
  char c;

  c = mySerial->read();
  while(true) {
    if (c != -1) {
      ans += String(c);

      if (ans.endsWith("OK\r\n")) {
        Serial.println(ans);
        break;
      }
      else if (ans.endsWith("ERROR\r\n")) {
        Serial.println(ans);
        break;
      }
      else if (b && ans.endsWith("\r\n")) {
        if (ans.length() >= 12) {
          ans = ans.substring(0, 12);
          Serial.println(ans);
          if (digitalRead(BT_JUMPER) == HIGH) {
            if (ans.equals("000000000000"))
              ans = "";
            btaddress = ans;
          }
          break;
        }
        else if (ans.length() >= 7) {
          Serial.println(ans);
          ans = ans.substring(0, ans.length() - 2);
          break;
        }

        Serial.println(ans);
        ans = "";
      }
    }

    c = mySerial->read();
  }

  return;
}

