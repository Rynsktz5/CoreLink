class SeenCache {

    private val seen = HashSet<String>()

    fun isSeen(id: String): Boolean {
        return seen.contains(id)
    }

    fun markSeen(id: String) {
        seen.add(id)
    }
}