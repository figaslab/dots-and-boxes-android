package com.devfigas.dotsandboxes.debug

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.devfigas.dotsandboxes.R
import com.devfigas.mockpvp.PvpGameFactoryRegistry
import com.devfigas.mockpvp.emoji.EmojiManager
import com.devfigas.mockpvp.model.User

class DebugMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_menu)

        findViewById<View>(R.id.btn_view_tutorial).setOnClickListener {
            val factory = PvpGameFactoryRegistry.get()
            val user = User(name = "Player", avatar = EmojiManager.getCurrentAvatarId(this))
            factory.getTutorialIntent(this, user)?.let { startActivity(it) }
        }
    }
}
