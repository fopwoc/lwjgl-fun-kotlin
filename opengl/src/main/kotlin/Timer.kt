import org.lwjgl.glfw.GLFW.glfwGetTime

class Timer {
    /**
     * Getter for the last loop time.
     *
     * @return System time of the last loop
     */
    /**
     * System time since last loop.
     */
    var lastLoopTime = 0.0
        private set

    /**
     * Used for FPS and UPS calculation.
     */
    private var timeCount = 0f

    /**
     * Frames per second.
     */
    private var fps = 0

    /**
     * Counter for the FPS calculation.
     */
    private var fpsCount = 0

    /**
     * Updates per second.
     */
    private var ups = 0

    /**
     * Counter for the UPS calculation.
     */
    private var upsCount = 0

    /**
     * Initializes the timer.
     */
    fun init() {
        lastLoopTime = time
    }

    val time: Double
        /**
         * Returns the time elapsed since `glfwInit()` in seconds.
         *
         * @return System time in seconds
         */
        get() = glfwGetTime()

    /**
     * Returns the time that have passed since the last loop.
     *
     * @return Delta time in seconds
     */
    val delta: Float
        get() {
            val time = time
            val delta = (time - lastLoopTime).toFloat()
            lastLoopTime = time
            timeCount += delta
            return delta
        }

    /**
     * Updates the FPS counter.
     */
    fun updateFPS() {
        fpsCount++
    }

    /**
     * Updates the UPS counter.
     */
    fun updateUPS() {
        upsCount++
    }

    /**
     * Updates FPS and UPS if a whole second has passed.
     */
    fun update() {
        if (timeCount > 1f) {
            fps = fpsCount
            fpsCount = 0
            ups = upsCount
            upsCount = 0
            timeCount -= 1f
        }
    }

    val fPS: Int
        /**
         * Getter for the FPS.
         *
         * @return Frames per second
         */
        get() = if (fps > 0) fps else fpsCount
    val uPS: Int
        /**
         * Getter for the UPS.
         *
         * @return Updates per second
         */
        get() = if (ups > 0) ups else upsCount

}
