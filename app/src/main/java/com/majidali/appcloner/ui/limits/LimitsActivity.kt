package com.majidali.appcloner.ui.limits

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.majidali.appcloner.databinding.ActivityLimitsBinding

/**
 * The Limits screen: what App Cloner can and cannot do, in plain
 * words, before the user finds out the hard way.
 *
 * The honest version, kept short:
 * - Work profile clones are real installs: normal notifications,
 *   and banking apps work in them, because the clone is a genuine
 *   second install, not a trick.
 * - Sandbox clones (V2) are different: banking will not work there,
 *   and notifications may arrive late, because the sandbox cannot
 *   pass the checks banks rely on.
 * - No promise of 100 live accounts. One phone can hold a few
 *   copies; a hundred accounts on one phone gets noticed and banned
 *   by the apps themselves.
 * - V1 allows 2 clones. The profile itself allows one extra copy of
 *   each app; the limit keeps the phone fast.
 * - Proxy is off by default. It is an advanced option for V2 only,
 *   and it does not hide the phone — a proxy can leak the real
 *   address, so it is never sold as protection.
 * - Updates do not break clones: a clone is a separate install, so
 *   updating the original app leaves the clone alone. If a clone
 *   ever stops opening after an update, re-clone it from its detail
 *   screen.
 */
class LimitsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLimitsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLimitsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
