package pt.ipleiria.estg.meicm.butler

data class Room(val roomName: String, val ip: String?) {
    constructor(roomName: String) : this(roomName,null)
}

