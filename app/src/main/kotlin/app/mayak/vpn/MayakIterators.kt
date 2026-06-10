package app.mayak.vpn

import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface

class BoxStringIterator(values: Iterable<String>) : StringIterator {
    private val values = values.toList()
    private val iterator = this.values.iterator()

    override fun len(): Int = values.size
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): String = iterator.next()
}

class BoxNetworkInterfaceIterator(values: Iterable<BoxNetworkInterface>) : NetworkInterfaceIterator {
    private val iterator = values.iterator()

    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): BoxNetworkInterface = iterator.next()
}
