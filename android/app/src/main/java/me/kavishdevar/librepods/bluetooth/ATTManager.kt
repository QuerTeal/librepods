/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import android.os.SystemClock
import java.io.Closeable

private const val ATT_PSM = 31
private val ATT_UUID = ParcelUuid.fromString("00000000-0000-0000-0000-000000000000")

/** One short-lived channel per request; no optimistic cache or persistent ATT connection. */
class ATTManagerv2 : Closeable {
    private val client = ConfirmedAttClient(SystemClock::elapsedRealtime)
    val states = client.states
    val sessionId get() = client.sessionId

    fun connected(adapter: BluetoothAdapter, device: BluetoothDevice) {
        client.connected {
            val socket = createBluetoothSocket(adapter, device, ATT_UUID, ATT_PSM)
            object : AttChannel {
                override fun connect() = socket.connect()
                override fun send(pdu: ByteArray) {
                    socket.outputStream.write(pdu)
                    socket.outputStream.flush()
                }
                override fun receive(): ByteArray? {
                    val buffer = ByteArray(512)
                    val count = socket.inputStream.read(buffer)
                    return if (count > 0) buffer.copyOf(count) else null
                }
                override fun close() = socket.close()
            }
        }
    }

    fun disconnected() = client.disconnected()
    fun getCharacteristic(handle: ATTHandles): ByteArray? =
        client.cached(handle) ?: client.read(handle)
    fun readCharacteristic(handle: ATTHandles, timeoutMillis: Long = 3000): ByteArray? =
        client.read(handle, timeoutMillis)
    fun writeCharacteristic(handle: ATTHandles, data: ByteArray, timeoutMillis: Long = 3000,
                            expectedSession: Long? = null): Boolean =
        client.write(handle, data, timeoutMillis, expectedSession)
    override fun close() = client.close()
}
