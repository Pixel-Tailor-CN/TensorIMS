package app.mystery0.ims.tensor.bridge

/** 记录单轮电脑启动的领取权；调用方负责用 EmbeddedConnection 的同一把锁保护。 */
class ComputerBootstrapLease {
    private var challenge: String? = null
    private var generation: Long = -1L
    private var epoch: Long = -1L
    private var identityDigest: String? = null
    private var deadline: Long = 0L
    private var claimantUid: Int? = null

    fun prepare(
        challenge: String,
        generation: Long,
        epoch: Long,
        identityDigest: String,
        deadline: Long,
    ) {
        require(challenge.isNotBlank() && generation >= 0 && epoch >= 0)
        require(identityDigest.isNotBlank() && deadline >= 0)
        this.challenge = challenge
        this.generation = generation
        this.epoch = epoch
        this.identityDigest = identityDigest
        this.deadline = deadline
        claimantUid = null
    }

    fun claim(now: Long, epoch: Long, identityDigest: String, callerUid: Int): String? {
        if (callerUid != SHELL_UID && callerUid != ROOT_UID) return null
        if (challenge == null || claimantUid != null || now < 0 || now >= deadline) return null
        if (this.epoch != epoch || this.identityDigest != identityDigest) return null
        claimantUid = callerUid
        return challenge
    }

    fun permitsDelivery(
        challenge: String,
        generation: Long,
        now: Long,
        epoch: Long,
        identityDigest: String,
        callerUid: Int,
    ): Boolean =
        this.challenge == challenge && this.generation == generation &&
            now >= 0 && now < deadline && this.epoch == epoch &&
            this.identityDigest == identityDigest && claimantUid == callerUid

    fun revoke(generation: Long) {
        if (this.generation != generation) return
        challenge = null
        this.generation = -1L
        epoch = -1L
        identityDigest = null
        deadline = 0L
        claimantUid = null
    }

    private companion object {
        const val ROOT_UID = 0
        const val SHELL_UID = 2000
    }
}
