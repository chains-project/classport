
JAVA_HOME ?= $(shell echo $$JAVA_HOME)
CFLAGS = -shared -fPIC -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
TARGET = agent.so
SRC = agent.c

all: $(TARGET)

$(TARGET): $(SRC)
	gcc -o $(TARGET) $(CFLAGS) $(SRC)

clean:
	rm -f $(TARGET)