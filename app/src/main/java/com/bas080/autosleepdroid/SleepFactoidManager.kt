@file:Suppress("MagicNumber", "MaxLineLength")

package com.bas080.autosleepdroid

import android.content.Context
import java.util.Calendar

data class SleepFactoid(
    val id: Int,
    val text: String,
    val category: String
)

object SleepFactoidManager {

    val FACTOIDS: List<SleepFactoid> = listOf(
        // Brain Refresh & Focus (1-20)
        SleepFactoid(1, "Deep sleep acts like a dishwasher for your brain, washing away waste built up during the day so you wake up clear-headed.", "Brain Refresh"),
        SleepFactoid(2, "Getting enough sleep helps your brain organize and lock in new memories and things you learned today.", "Brain Refresh"),
        SleepFactoid(3, "Sleeping well improves your focus and reaction time tomorrow, keeping you sharp and alert.", "Brain Refresh"),
        SleepFactoid(4, "When you sleep, your brain recharges its decision-making muscles so choices feel easier tomorrow.", "Brain Refresh"),
        SleepFactoid(5, "A full night's sleep boosts creativity, helping your mind solve problems that seemed tricky yesterday.", "Brain Refresh"),
        SleepFactoid(6, "Deep sleep calms your brain's emotional center, helping you handle stress with ease.", "Brain Refresh"),
        SleepFactoid(7, "Skimping on sleep slows your brain's processing speed—similar to running a phone on low battery.", "Brain Refresh"),
        SleepFactoid(8, "During sleep, your brain cleans up cluttered neural pathways, making space for new ideas tomorrow.", "Brain Refresh"),
        SleepFactoid(9, "Sleep strengthens mental endurance, helping you stay patient and focused on long tasks.", "Brain Refresh"),
        SleepFactoid(10, "Your brain processes complex emotions while you sleep, letting you wake up with a fresh mindset.", "Brain Refresh"),
        SleepFactoid(11, "A well-rested brain can switch between tasks much faster and with fewer mistakes.", "Brain Refresh"),
        SleepFactoid(12, "REM sleep—the dreaming stage—helps your brain connect unrelated ideas into creative solutions.", "Brain Refresh"),
        SleepFactoid(13, "Consistent sleep helps maintain long-term brain health and keeps your memory sharp as you age.", "Brain Refresh"),
        SleepFactoid(14, "Shortchanging your sleep makes your brain work twice as hard to do the same everyday tasks.", "Brain Refresh"),
        SleepFactoid(15, "Good sleep helps your brain filter out background distractions so you can focus on what matters.", "Brain Refresh"),
        SleepFactoid(16, "Sleep balances your brain chemicals, helping you feel naturally motivated and positive tomorrow.", "Brain Refresh"),
        SleepFactoid(17, "Your brain rehearses motor skills—like sports or instruments—while you sleep peacefully.", "Brain Refresh"),
        SleepFactoid(18, "Resting your eyes and mind in a dark room resets mental fatigue, even before deep sleep sets in.", "Brain Refresh"),
        SleepFactoid(19, "A complete sleep cycle takes about 90 minutes; completing full cycles leaves you feeling energized.", "Brain Refresh"),
        SleepFactoid(20, "Protecting your sleep is the single most effective way to improve your brainpower every day.", "Brain Refresh"),

        // Energy & Body Recovery (21-40)
        SleepFactoid(21, "Your body releases growth hormones while you sleep to repair muscles and rebuild tissue.", "Body Recovery"),
        SleepFactoid(22, "Sleep builds a stronger immune system, helping your body fight off colds and infections.", "Body Recovery"),
        SleepFactoid(23, "Getting enough sleep helps balance hunger hormones, making healthy food choices easier tomorrow.", "Body Recovery"),
        SleepFactoid(24, "While you sleep, your heart rate and blood pressure drop, giving your heart a well-deserved rest.", "Body Recovery"),
        SleepFactoid(25, "Sleep helps regulate blood sugar levels, keeping your energy stable throughout the day.", "Body Recovery"),
        SleepFactoid(26, "Good sleep lowers inflammation in your body, speeding up physical recovery from workouts.", "Body Recovery"),
        SleepFactoid(27, "When you sleep well, your body naturally conserves and replenishes stamina for the morning.", "Body Recovery"),
        SleepFactoid(28, "Sleep helps balance your body's hydration and stress hormones for healthier skin.", "Body Recovery"),
        SleepFactoid(29, "Deep rest reduces physical tension in your neck, shoulders, and back built up during the day.", "Body Recovery"),
        SleepFactoid(30, "Your stomach and digestive system heal and reset while you rest comfortably at night.", "Body Recovery"),
        SleepFactoid(31, "Consistent sleep schedule keeps your core body temperature and metabolism running smoothly.", "Body Recovery"),
        SleepFactoid(32, "Even one full night of good sleep measurably improves your physical strength and stamina.", "Body Recovery"),
        SleepFactoid(33, "Sleep gives your eyes a chance to rehydrate and rest after staring at screens all day.", "Body Recovery"),
        SleepFactoid(34, "Your nervous system relaxes during deep sleep, lowering your baseline stress levels.", "Body Recovery"),
        SleepFactoid(35, "Proper rest improves physical coordination and balance, reducing accidental trips or stumbles.", "Body Recovery"),
        SleepFactoid(36, "Sleep helps regulate thyroid hormones, keeping your body's thermostat balanced.", "Body Recovery"),
        SleepFactoid(37, "Your joints and spinal discs decompress and rehydrate while lying comfortably in bed.", "Body Recovery"),
        SleepFactoid(38, "Restful sleep helps prevent afternoon energy crashes that lead to heavy caffeine reliance.", "Body Recovery"),
        SleepFactoid(39, "Sleep boosts collagen production, repairing skin cells and keeping you looking refreshed.", "Body Recovery"),
        SleepFactoid(40, "Taking care of your body through sleep pays off with higher daily energy and fewer sick days.", "Body Recovery"),

        // Natural Rhythms & Light (41-60)
        SleepFactoid(41, "Dimming lights 1 hour before bed signals to your body that it's time to start producing melatonin.", "Rhythms & Light"),
        SleepFactoid(42, "Bright morning sunlight resets your internal clock, making it easier to fall asleep at night.", "Rhythms & Light"),
        SleepFactoid(43, "A cool bedroom—around 65°F or 18°C—mimics your body's natural temperature drop for sleep.", "Rhythms & Light"),
        SleepFactoid(44, "Caffeine can stay in your system for up to 8 hours; avoiding it in the afternoon protects your sleep.", "Rhythms & Light"),
        SleepFactoid(45, "Going to bed around the same time every night trains your body to fall asleep faster.", "Rhythms & Light"),
        SleepFactoid(46, "Blue light from phone screens tricks your brain into thinking it is daytime—use night mode in the evening.", "Rhythms & Light"),
        SleepFactoid(47, "A warm bath or shower before bed cools your body down afterward, signaling sleep readiness.", "Rhythms & Light"),
        SleepFactoid(48, "Your body loves rhythm: sticking to a consistent wake-up time improves overall sleep quality.", "Rhythms & Light"),
        SleepFactoid(49, "Heavy meals right before bed keep your digestion working hard, which can interrupt deep sleep.", "Rhythms & Light"),
        SleepFactoid(50, "Keeping your bedroom dark with blackout curtains or an eye mask promotes longer uninterrupted sleep.", "Rhythms & Light"),
        SleepFactoid(51, "Getting 10–15 minutes of outdoor light within an hour of waking up sets your sleep clock for tonight.", "Rhythms & Light"),
        SleepFactoid(52, "Soft, warm yellow or amber lighting in the evening helps your eyes and mind relax.", "Rhythms & Light"),
        SleepFactoid(53, "Alcohol may make you feel sleepy, but it disrupts REM sleep later in the night.", "Rhythms & Light"),
        SleepFactoid(54, "Fresh, circulating air in a well-ventilated bedroom improves overall sleep depth.", "Rhythms & Light"),
        SleepFactoid(55, "Your body temperature naturally dips in the evening—a blanket helps keep you comfortable.", "Rhythms & Light"),
        SleepFactoid(56, "Avoiding high-intensity workouts within 2 hours of bedtime helps your heart rate settle down.", "Rhythms & Light"),
        SleepFactoid(57, "Keeping screens out of the bedroom turns your bed into a sanctuary dedicated to rest.", "Rhythms & Light"),
        SleepFactoid(58, "Consistent light-dark signals are the most powerful tool for naturally overcoming jet lag.", "Rhythms & Light"),
        SleepFactoid(59, "Wearing warm socks to bed warms your feet, which helps lower your core body temperature faster.", "Rhythms & Light"),
        SleepFactoid(60, "Listening to your body's natural tiredness cues prevents lying awake in bed.", "Rhythms & Light"),

        // Stress & Mood Balance (61-80)
        SleepFactoid(61, "A good night's sleep acts as an emotional buffer, making everyday stresses feel smaller.", "Stress & Mood"),
        SleepFactoid(62, "When you get enough sleep, your brain is far better at staying calm during busy days.", "Stress & Mood"),
        SleepFactoid(63, "Sleep reduces cortisol—the body's primary stress hormone—leaving you feeling relaxed.", "Stress & Mood"),
        SleepFactoid(64, "Being well-rested improves social connection, making it easier to communicate kindly.", "Stress & Mood"),
        SleepFactoid(65, "Sleep restores patience and emotional resilience so minor setbacks don't derail your day.", "Stress & Mood"),
        SleepFactoid(66, "Writing down lingering worries on a piece of paper before bed clears mental clutter.", "Stress & Mood"),
        SleepFactoid(67, "Sleep deprivation can amplify anxiety; prioritizing sleep is a powerful self-care habit.", "Stress & Mood"),
        SleepFactoid(68, "Restful sleep boosts empathy and active listening, strengthening relationships with loved ones.", "Stress & Mood"),
        SleepFactoid(69, "Taking time to unwind before bed teaches your brain that nighttime is safe and peaceful.", "Stress & Mood"),
        SleepFactoid(70, "Sleep helps prevent burnout by giving your mind a complete break from daily responsibilities.", "Stress & Mood"),
        SleepFactoid(71, "A gentle wind-down routine helps transition your nervous system from 'busy mode' to 'rest mode'.", "Stress & Mood"),
        SleepFactoid(72, "Celebrating small wins before going to sleep promotes positive feelings and peaceful dreams.", "Stress & Mood"),
        SleepFactoid(73, "Self-compassion around sleep helps: if you can't sleep immediately, gentle rest still recharges you.", "Stress & Mood"),
        SleepFactoid(74, "Sleep improves emotional control, making it easier to stay calm in tense situations.", "Stress & Mood"),
        SleepFactoid(75, "Comfortable bedding and soothing ambient sounds create a sense of safety that invites sleep.", "Stress & Mood"),
        SleepFactoid(76, "Giving yourself permission to pause and sleep is essential for long-term mental well-being.", "Stress & Mood"),
        SleepFactoid(77, "Deep breathing in bed lowers your heart rate and signals safety to your nervous system.", "Stress & Mood"),
        SleepFactoid(78, "A peaceful sleep environment helps quiet internal overthinking after a demanding day.", "Stress & Mood"),
        SleepFactoid(79, "Consistent sleep leads to more stable energy and a brighter, more optimistic outlook.", "Stress & Mood"),
        SleepFactoid(80, "Resting well tonight is a gift to your future self tomorrow.", "Stress & Mood"),

        // Easy Wind-Down Habits (81-100)
        SleepFactoid(81, "Try 4-7-8 breathing: inhale for 4 seconds, hold for 7, and exhale slowly for 8 to relax.", "Wind-Down Habits"),
        SleepFactoid(82, "Listening to soft ambient soundscapes or rain audio helps quiet a chatter-filled mind.", "Wind-Down Habits"),
        SleepFactoid(83, "Reading a relaxing book or listening to an audiobook is a great screen-free way to unwind.", "Wind-Down Habits"),
        SleepFactoid(84, "Stretching gently for 5 minutes releases physical tension stored in your muscles.", "Wind-Down Habits"),
        SleepFactoid(85, "A cup of warm herbal tea—like chamomile or peppermint—signals it's time to slow down.", "Wind-Down Habits"),
        SleepFactoid(86, "Progressive relaxation—tensing then relaxing each muscle group from toes to head—induces sleep.", "Wind-Down Habits"),
        SleepFactoid(87, "Setting an alarm for bedtime reminds you to wrap up activities before tiredness passes.", "Wind-Down Habits"),
        SleepFactoid(88, "Putting your phone on a charger across the room removes late-night scrolling temptations.", "Wind-Down Habits"),
        SleepFactoid(89, "Dimming household lights 30 minutes before bed helps set a peaceful evening mood.", "Wind-Down Habits"),
        SleepFactoid(90, "Using a low-friction sleep timer app ensures media pauses smoothly after you fall asleep.", "Wind-Down Habits"),
        SleepFactoid(91, "Soothing instrumental music or white noise masks sudden household background noises.", "Wind-Down Habits"),
        SleepFactoid(92, "Keeping a bedtime journal helps transfer thoughts out of your head onto paper.", "Wind-Down Habits"),
        SleepFactoid(93, "Focusing on the sensation of your body sinking into the mattress helps anchor your mind.", "Wind-Down Habits"),
        SleepFactoid(94, "A simple 10-minute bedtime ritual—like brushing teeth and dimming lights—prepares your brain.", "Wind-Down Habits"),
        SleepFactoid(95, "Replacing late-night doomscrolling with soothing audio aids faster sleep initiation.", "Wind-Down Habits"),
        SleepFactoid(96, "Inhaling calming scents like lavender or cedarwood can promote a feeling of relaxation.", "Wind-Down Habits"),
        SleepFactoid(97, "If your mind feels active, counting slow breaths backward from 100 occupies busy thoughts.", "Wind-Down Habits"),
        SleepFactoid(98, "Keeping room temperature cool and using breathable cotton bedding increases deep sleep duration.", "Wind-Down Habits"),
        SleepFactoid(99, "Reminding yourself that tomorrow's tasks can wait until morning brings immediate relief.", "Wind-Down Habits"),
        SleepFactoid(100, "Small, consistent wind-down habits create powerful long-term sleep improvements.", "Wind-Down Habits")
    )

    fun getTodayFactoid(context: Context): SleepFactoid {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val dislikedSet = prefs.getStringSet(PreferenceKeys.KEY_DISLIKED_FACTOIDS, emptySet()) ?: emptySet()

        val available = FACTOIDS.filter { it.id.toString() !in dislikedSet }
        val listToUse = if (available.isNotEmpty()) available else FACTOIDS

        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val index = Math.abs(dayOfYear) % listToUse.size
        return listToUse[index]
    }

    fun dislikeFactoid(context: Context, factoidId: Int): SleepFactoid {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val dislikedSet = prefs.getStringSet(PreferenceKeys.KEY_DISLIKED_FACTOIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        dislikedSet.add(factoidId.toString())
        prefs.edit().putStringSet(PreferenceKeys.KEY_DISLIKED_FACTOIDS, dislikedSet).apply()

        return getTodayFactoid(context)
    }
}
