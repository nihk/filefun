package nick.filefun

object IdGenerator {
    private var count = 1

    fun next(): Int = count++
}