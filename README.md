# 🌌 AstraRedstoneSystems
**Build complex redstone systems without cables using logic blocks, memory, timers, wireless signals and data processing.**

Created and maintained by **DawcoU** 👨–

AstraRedstoneSystems is a powerful tool for technical players and map makers, allowing you to create complex Redstone mechanisms using dedicated logic blocks. Written in **Java 17** and optimized specifically for **Paper** (and its forks) to ensure maximum performance and tick-accuracy. ⚡

---

### 🚀 Key Features
* **Core Logic Gates:** Includes all basic gates like **NOT, AND, OR, XOR, NAND, NOR, XNOR**. 🧩
* **Data Processing (NEW):** Advanced gates for math, variables, and comparisons. 🔢
* **Wireless Data Link:** Transfer numbers and signals between gates without cables using `/alg link`. 🔗
* **Memory & Timing:** RS-Latch, T-Flip-Flop, and enhanced repeaters with delays up to several seconds.
* **Long-Range Sensing:** Motion sensors that work at a distance. 📡

---

### 🛠️ Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bramka <category> <type>` | Receive a specific logic gate block | `astrars.gates` |
| `/bramka <category> <type> <param>` | Receive a gate with parameter (e.g. `DECODER 5`) | `astrars.gates` |
| `/alg link` | Link two gates to transfer data wirelessly | `astrars.admin` |

---

### 📖 How to use (Tutorial)

**1. Basic Logic Gates:**
* Use the command `/bramka <category> <type>` (e.g., `/bramka logic AND`) to receive a logic block.
* Place the block on the ground. The output will face the **direction you were looking** when placing it.
* **Input/Output Sides:** * **Back/Sides:** Inputs for signal or data.
  * **Front:** Main output (faces away from you during placement).

**2. Data & Math Gates (Kalkulator System):**
These gates don't just send ON/OFF signals, they send **numbers**! 🔢

* **VARIABLE_GATE:** Stores a number. It sends its value to the output immediately when it receives it. Perfect for memory in a calculator.
* **MATH_GATE:** Has modes like `ADD`, `SUB`, `MUL`, `DIV` and `POW`. Connect data to the **left** and **right** side to perform calculations.
* **COMPARATOR:** Compares two numbers. Modes: `==`, `>`, `<`, `>=`, `<=`, `!=`. If the condition is met, it outputs a Redstone signal.
* **NUMBER_GATE:** A fixed value block. When powered from the back, it sends its preset number forward.
* **BOOLEAN_GATE:** The bridge between Redstone and Data. It converts a standard signal into numbers: ON = 1, OFF = 0. Fully compatible with Math and Variable gates!
* **DECODER:** Outputs a signal only if the received number matches its target (e.g., a Decoder set to 5 will only turn on when it gets the number 5).
* **LINKER:** A "data valve". It lets the number through from the left side, UNLESS it's powered from the right side (which acts as a block/brake).

**3. Wireless Data Linking (`/alg link`):**
Want to connect a `NUMBER_GATE` to a `MATH_GATE` 50 blocks away? Use the Linking System! 🔗

1. Go to the **Source Gate** (e.g., the block that sends the number).
2. Type `/alg link`.
3. Go to the **Target Gate** (the block that should receive the data).
4. Type `/alg link` again.
5. **Boom!** The gates are now connected. Whenever the source updates, the target gets the data instantly! ⚡

**4. Wireless Redstone (Bluetooth System):**
* **SENDER:** Type `/bramka data SENDER <channel>`. Connect Redstone to it.
* **RECEIVER:** Type `/bramka data RECEIVER <channel>`. It will output signal whenever the sender on the same channel is active.

---

### 🛠️ Other Projects
🛡️ **[AstraLogin](https://modrinth.com/plugin/astralogin)** - Check out my other plugin! It's a modern, secure, and lightweight login system featuring Bcrypt hashing and advanced player protection.

---

# Links 💾

**GitHub AstraRedstoneSystems:** [https://github.com/DawcoU/AstraRedstoneSystems](https://github.com/DawcoU/AstraRedstoneSystems) 🖥️

---

### 🌐 Support & Community
If you need help, want to report a bug, or follow the development by **DawcoU**, join our Discord: [https://discord.gg/XR9UUjZv](https://discord.gg/XR9UUjZv) 💬

---
_AstraRedstoneSystems - Making Redstone Smart. ⚡_