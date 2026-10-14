package com.baroness.app.data

import com.baroness.app.models.VibeQuote

object SignatureLoops {
    private const val BASE_URL = "https://baroness-test.vercel.app"

    val quotes = listOf(
        /*===========================================
        <font|position>
              left|right|center and indent:20|40|80
        ===========================================*/

        // ─── Test quote (kept active) ───
        VibeQuote(
            part1 = "NO \"<Kaushan|center>NOT TODAY</> \"",
            part2 = "<gamaamli|left>REMEMBER</>\n<yuyu|indent:80>Everyday is a Hustle</>",
            photo1 = "$BASE_URL/bucket/Image-81.jpg",
            photo2 = "$BASE_URL/bucket/Image-82.jpg"
        ),

        // ─── Actives 01–19 ───
        // 01
        /*VibeQuote(
            part1 = "I don't know what I did to deserve a best friend like you",
            part2 = "but I'm so grateful to have you in my life",
            photo1 = "$BASE_URL/bucket/Image-81.jpg",
            photo2 = "$BASE_URL/bucket/Image-82.jpg"
        ),
        // 02
        VibeQuote(
            part1 = "..but you're my best friend & I can't lose you :(",
            part2 = "Thank You for being my unpaid therapist🙂↕️",
            photo1 = "$BASE_URL/bucket/Image-27.jpg",
            photo2 = "$BASE_URL/bucket/Image-28.jpg"
        ),
        // 03
        VibeQuote(
            part1 = "Cheers to our FRIEND-CHIP!🥂",
            part2 = "u'm.?? !t's forever kind thing :)",
            photo1 = "$BASE_URL/bucket/Image-31.jpg",
            photo2 = "$BASE_URL/bucket/Image-32.jpg"
        ),
        // 04
        VibeQuote(
            part1 = "Every Tall friend need a short best friend🥴",
            part2 = "you SHORT!!, I'm literally older than you😂",
            photo1 = "$BASE_URL/bucket/Image-33.jpg",
            photo2 = "$BASE_URL/bucket/Image-34.jpg"
        ),
        // 05
        VibeQuote(
            part1 = "..and till the END you're stuck with me",
            part2 = "I'll always be there to irritate you",
            photo1 = "$BASE_URL/bucket/Image-35.jpg",
            photo2 = "$BASE_URL/bucket/Image-36.jpg"
        ),
        // 06
        VibeQuote(
            part1 = "Thank You For Existing",
            part2 = "My Favourite Human ❤️",
            photo1 = "$BASE_URL/bucket/Image-39.jpg",
            photo2 = "$BASE_URL/bucket/Image-40.jpg"
        ),
        // 07 — font span: "My Wish" in Kaushan Script
        VibeQuote(
            part1 = "11:11 \n You are <kaushan>My Wish</> :)",
            part2 = "☆☆..counting my lucky stars",
            photo1 = "$BASE_URL/bucket/Image-43.jpg",
            photo2 = "$BASE_URL/bucket/Image-44.jpg"
        ),
        // 08
        VibeQuote(
            part1 = "sometimes u just need to do bad things with good friends",
            part2 = "The best memories comes from the bad ideas done with best friends",
            photo1 = "$BASE_URL/bucket/Image-49.jpg",
            photo2 = "$BASE_URL/bucket/Image-50.jpg"
        ),
        // 09
        VibeQuote(
            part1 = "you are special to me",
            part2 = "let whoever think whatever",
            photo1 = "$BASE_URL/bucket/Image-55.jpg",
            photo2 = "$BASE_URL/bucket/Image-56.jpg"
        ),
        // 10
        VibeQuote(
            part1 = "you are crazy I like that..😂",
            part2 = "REMEMBER: you've survived every (I CANT DO THIS) so far",
            photo1 = "$BASE_URL/bucket/Image-57.jpg",
            photo2 = "$BASE_URL/bucket/Image-58.jpg"
        ),
        // 11 — font span: "wish" in Kaushan Script
        VibeQuote(
            part1 = "I still <kaushan>wish</> for you at 11:11 🥹",
            part2 = "she was simple like quantum physics",
            photo1 = "$BASE_URL/bucket/Image-59.jpg",
            photo2 = "$BASE_URL/bucket/Image-60.jpg"
        ),
        // 12
        VibeQuote(
            part1 = "you are the trouble am in",
            part2 = "trouble never looked so goddamn fine",
            photo1 = "$BASE_URL/bucket/Image-67.jpg",
            photo2 = "$BASE_URL/bucket/Image-68.jpg"
        ),
        // 13
        VibeQuote(
            part1 = "there is fire between us",
            part2 = "a beautiful thing is never perfect",
            photo1 = "$BASE_URL/bucket/Image-73.jpg",
            photo2 = "$BASE_URL/bucket/Image-74.jpg"
        ),
        // 14
        VibeQuote(
            part1 = "avec toi, je suis moi",
            part2 = "tout pour toi..",
            photo1 = "$BASE_URL/bucket/Image-83.jpg",
            photo2 = "$BASE_URL/bucket/Image-84.jpg"
        ),
        // 15
        VibeQuote(
            part1 = "...but u can call me love",
            part2 = "I call you shawty",
            photo1 = "$BASE_URL/bucket/Image-85.jpg",
            photo2 = "$BASE_URL/bucket/Image-86.jpg"
        ),
        // 16
        VibeQuote(
            part1 = "you are my euphoria",
            part2 = "more than a wife u are my best friend",
            photo1 = "$BASE_URL/bucket/Image-87.jpg",
            photo2 = "$BASE_URL/bucket/Image-88.jpg"
        ),
        // 17
        VibeQuote(
            part1 = "That's him:\nMy Moonbeam",
            part2 = "That's her:\nLady of the Moon",
            photo1 = "$BASE_URL/bucket/Image-91.jpg",
            photo2 = "$BASE_URL/bucket/Image-92.jpg"
        ),
        // 18
        VibeQuote(
            part1 = "To Infinity and Beyond",
            part2 = "...to ∞ & →",
            photo1 = "$BASE_URL/bucket/Image-93.jpg",
            photo2 = "$BASE_URL/bucket/Image-94.jpg"
        ),
        // 19
        VibeQuote(
            part1 = "pure & raw",
            part2 = "boom ShaKaLaka",
            photo1 = "$BASE_URL/bucket/Image-95.jpg",
            photo2 = "$BASE_URL/bucket/Image-96.jpg"
        ),

        // ─── Actives 20–43 ───

        // 20
        VibeQuote(
            part1 = "mh youstupid :)",
            part2 = "I'm so Youholic",
            photo1 = "$BASE_URL/bucket/Image-75.jpg",
            photo2 = "$BASE_URL/bucket/Image-76.jpg"
        ),
        // 21
        VibeQuote(
            part1 = "its BF",
            part2 = "No its FB",
            photo1 = "$BASE_URL/bucket/Image-1.jpg",
            photo2 = "$BASE_URL/bucket/Image-2.JPG"
        ),
        // 22
        VibeQuote(
            part1 = "I need my own planet y'all irritates me",
            part2 = "bonjour motherfvcker",
            photo1 = "$BASE_URL/bucket/Image-3.jpg",
            photo2 = "$BASE_URL/bucket/Image-4.JPG"
        ),
        // 23
        VibeQuote(
            part1 = "main character energy",
            part2 = "Bas vibe match honi chahiye",
            photo1 = "$BASE_URL/bucket/Image-53.jpg",
            photo2 = "$BASE_URL/bucket/Image-54.jpg"
        ),
        // 24
        VibeQuote(
            part1 = "..but is she me ?",
            part2 = "no face just vibes",
            photo1 = "$BASE_URL/bucket/Image-11.JPG",
            photo2 = "$BASE_URL/bucket/Image-12.JPG"
        ),
        // 25
        VibeQuote(
            part1 = "trying to remember who you where",
            part2 = "You know me .?",
            photo1 = "$BASE_URL/bucket/Image-13.jpg",
            photo2 = "$BASE_URL/bucket/Image-14.jpg"
        ),
        // 26
        VibeQuote(
            part1 = "WHo's this DIVA???",
            part2 = "This is me",
            photo1 = "$BASE_URL/bucket/Image-19.jpg",
            photo2 = "$BASE_URL/bucket/Image-20.jpg"
        ),
        // 27
        VibeQuote(
            part1 = "I hate diabetes",
            part2 = "I'll be ur sugar daddy",
            photo1 = "$BASE_URL/bucket/Image-29.jpg",
            photo2 = "$BASE_URL/bucket/Image-30.jpg"
        ),
        // 28
        VibeQuote(
            part1 = "we're more than friends",
            part2 = "..but less than a couple",
            photo1 = "$BASE_URL/bucket/Image-37.jpg",
            photo2 = "$BASE_URL/bucket/Image-38.jpg"
        ),
        // 29
        VibeQuote(
            part1 = "yess continue..",
            part2 = "Do you understand..??",
            photo1 = "$BASE_URL/bucket/Image-47.jpg",
            photo2 = "$BASE_URL/bucket/Image-48.jpg"
        ),
        // 30
        VibeQuote(
            part1 = "More than friends",
            part2 = "Less than lovers",
            photo1 = "$BASE_URL/bucket/Image-5.JPG",
            photo2 = "$BASE_URL/bucket/Image-6.jpg"
        ),
        // 31
        VibeQuote(
            part1 = "Fvck butterflies",
            part2 = "I feel the whole zoo when i'm with you",
            photo1 = "$BASE_URL/bucket/Image-7.JPG",
            photo2 = "$BASE_URL/bucket/Image-8.JPG"
        ),
        // 32
        VibeQuote(
            part1 = "we're just friends trying like hell not to be lovers",
            part2 = "we play dump but we know exactly what we're doing",
            photo1 = "$BASE_URL/bucket/Image-77.jpg",
            photo2 = "$BASE_URL/bucket/Image-78.jpg"
        ),
        // 33 — font span: "Love" in Playfair Display
        VibeQuote(
            part1 = "Heart says, \"<playfair>Love</>\"",
            part2 = "Mind says, \"Friends\"",
            photo1 = "$BASE_URL/bucket/Image-15.jpg",
            photo2 = "$BASE_URL/bucket/Image-16.jpg"
        ),
        // 34
        VibeQuote(
            part1 = "it takes me days to recover after a dream with u in it",
            part2 = "By the way i'm wearing the smile u gave me.",
            photo1 = "$BASE_URL/bucket/Image-17.jpg",
            photo2 = "$BASE_URL/bucket/Image-18.jpg"
        ),
        // 35
        VibeQuote(
            part1 = "You'll always be my favorite \"WHAT IF\"",
            part2 = "\"just friends\" became our favorite lie",
            photo1 = "$BASE_URL/bucket/Image-23.jpg",
            photo2 = "$BASE_URL/bucket/Image-24.jpg"
        ),
        // 36
        VibeQuote(
            part1 = "I didn't fall for u, you fvcking tripped me",
            part2 = "I wonder what I look like in your eyes.?",
            photo1 = "$BASE_URL/bucket/Image-41.jpg",
            photo2 = "$BASE_URL/bucket/Image-42.jpg"
        ),
        // 37
        VibeQuote(
            part1 = "I'd be his if he asked, but he never will",
            part2 = "Dating or not.. you're still mine",
            photo1 = "$BASE_URL/bucket/Image-45.jpg",
            photo2 = "$BASE_URL/bucket/Image-46.jpg"
        ),
        // 38
        VibeQuote(
            part1 = "you confuse me",
            part2 = "you and I will always be unfinished business",
            photo1 = "$BASE_URL/bucket/Image-51.jpg",
            photo2 = "$BASE_URL/bucket/Image-52.jpg"
        ),
        // 39
        VibeQuote(
            part1 = "Secrete #7: I want you. so be brave and want me back.",
            part2 = "Secrete #6: I stare at you when you're not looking.",
            photo1 = "$BASE_URL/bucket/Image-61.jpg",
            photo2 = "$BASE_URL/bucket/Image-62.jpg"
        ),
        // 40
        VibeQuote(
            part1 = "I cant unlove you",
            part2 = "real feelings dont just go away",
            photo1 = "$BASE_URL/bucket/Image-63.jpg",
            photo2 = "$BASE_URL/bucket/Image-64.jpg"
        ),
        // 41
        VibeQuote(
            part1 = "I wish he loved me like I love him",
            part2 = "plot twist: he loves you back",
            photo1 = "$BASE_URL/bucket/Image-65.jpg",
            photo2 = "$BASE_URL/bucket/Image-66.jpg"
        ),
        // 42
        VibeQuote(
            part1 = "c'mon, we'd look cute together",
            part2 = "You and I are one big \"its complicated\"",
            photo1 = "$BASE_URL/bucket/Image-69.jpg",
            photo2 = "$BASE_URL/bucket/Image-70.jpg"
        ),
        // 43
        VibeQuote(
            part1 = "More than a friendship less than a relationship",
            part2 = "Flirtationship;",
            photo1 = "$BASE_URL/bucket/Image-71.jpg",
            photo2 = "$BASE_URL/bucket/Image-72.jpg"
        )*/

    )
}