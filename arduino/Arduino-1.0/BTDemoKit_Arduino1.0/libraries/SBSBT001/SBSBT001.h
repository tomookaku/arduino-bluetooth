#ifndef __SBSBT001_h__
#define __SBSBT001_h__

#include "Arduino.h"

#include <string.h>
#include <SoftwareSerial.h>

// Bluetooth baudrate
#define BT_BAUD 9600
// Bluetooth shield pairing switch
#define BT_SW 8
// Bluetooth shield jumper
#define BT_JUMPER 9
// Bluetooth TX
#define BT_TX 6
// Bluetooth RX
#define BT_RX 7

class SBSBT001
{
private:
    const char *manufacturer;
    const char *model;
    const char *description;
    const char *version;
    const char *uri;
    const char *serial;
    bool connected;

	bool pairing;
	String response; 
	String btaddress;

	int  msg_length;
	int  msg_length2;
	byte msg[256];

	void readline(bool b);

	SoftwareSerial *mySerial;

public:
    SBSBT001(const char *manufacturer,
             const char *model,
             const char *description,
             const char *version,
             const char *uri,
             const char *serial);

	void begin(void);

    bool isConnected(void);
    bool isConnected2(void);

	void btConnectionCheck(void);
	void btPairingCheck(void);

    int read(byte *buff, int len);
    int write(byte *buff, int len);
};

#endif /* __SBSBT001_h__ */