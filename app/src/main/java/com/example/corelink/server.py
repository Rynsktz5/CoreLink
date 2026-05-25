import serial
import json

port = "COM4"  # CHANGE THIS

ser = serial.Serial(port, baudrate=9600)

print("🔥 Connected. Listening...")

while True:
    try:
        if ser.in_waiting:
            data = ser.readline().decode().strip()
            print("📩 Received:", data)

            reply = {
                "id": "laptop123",
                "senderId": "laptop",
                "receiverId": "android",
                "content": "🔥 Reply from Laptop",
                "timestamp": 123456,
                "ttl": 5
            }

            ser.write((json.dumps(reply) + "\n").encode())

    except Exception as e:
        print("Error:", e)