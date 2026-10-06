package com.surajdev.talkwise.data

object Scenarios {
    val free = Scenario(
        id = "free",
        title = "Free talk",
        subtitle = "Anything on your mind",
        role = "",
        simpleOpener = "Hi! I am Wise. How is your day today?",
        opener = "Hey, good to see you. What's been the best part of your day so far?",
    )

    val all = listOf(
        Scenario(
            id = "cafe",
            title = "Café order",
            subtitle = "Order a drink and a snack",
            role = "You are a friendly barista at a small café. The learner is a customer ordering.",
            simpleOpener = "Hello! Welcome to the café. What would you like to drink?",
            opener = "Hi there, welcome in! What can I get started for you today?",
        ),
        Scenario(
            id = "interview",
            title = "Job interview",
            subtitle = "Answer with confidence",
            role = "You are a kind but professional interviewer. The learner is the candidate. Ask one interview question at a time.",
            simpleOpener = "Hello, nice to meet you. Please tell me about yourself.",
            opener = "Thanks for coming in today. To start, could you walk me through your background?",
        ),
        Scenario(
            id = "airport",
            title = "Airport check-in",
            subtitle = "Bags, seats and boarding",
            role = "You are an airline check-in agent. The learner is a passenger checking in for a flight.",
            simpleOpener = "Good morning! Where are you flying today?",
            opener = "Good morning! May I see your passport, and where are you headed today?",
        ),
        Scenario(
            id = "neighbour",
            title = "New neighbour",
            subtitle = "Easy small talk",
            role = "You are a cheerful neighbour who has just met the learner in the hallway. Make light small talk.",
            simpleOpener = "Oh, hello! Are you my new neighbour? I am Sam.",
            opener = "Oh hi! You must have just moved in. I'm Sam from next door. How's the move going?",
        ),
        Scenario(
            id = "debate",
            title = "Friendly debate",
            subtitle = "Share and defend an opinion",
            role = "You are a playful debate partner. Pick everyday topics, take the opposite side politely and ask the learner to explain their view.",
            simpleOpener = "Let's play a game. Cats or dogs: which is better? Tell me why.",
            opener = "Let's warm up with a classic: is it better to live in a big city or a small town? Pick a side and convince me.",
        ),
    )
}
