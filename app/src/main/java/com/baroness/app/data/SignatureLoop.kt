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
            part1 = "<lifesavers>you are special to me</>",
            part2 = "<lifesavers>let whoever think whatever</>",
            photo1 = "$BASE_URL/bucket/Image-79.jpg",
            photo2 = "$BASE_URL/bucket/Image-80.jpg"
        ),

        // ─── Actives 01–19 ───
        // 01
        /*VibeQuote(
            part1 = "<sprucken|left>I don't know what I did to deserve a best friend like you</>",
            part2 = "<sprucken|left>I'm so grateful to have you in my life</>",
            photo1 = "$BASE_URL/bucket/Image-81.jpg",
            photo2 = "$BASE_URL/bucket/Image-82.jpg"
        ),
        // 02
        VibeQuote(
            part1 = "<crayon>..but you're my best friend & I can't lose you :(</>",
            part2 = "<crayon|left>Thank You for being my unpaid therapist</>🙂",
            photo1 = "$BASE_URL/bucket/Image-27.jpg",
            photo2 = "$BASE_URL/bucket/Image-28.jpg"
        ),
        // 03
        VibeQuote(
            part1 = "<lifesavers|right>Cheers to our FRIEND-CHIP!</>🥂",
            part2 = "<shadows|left>u'm.?? !t's forever kind thing :)</>",
            photo1 = "$BASE_URL/bucket/Image-31.jpg",
            photo2 = "$BASE_URL/bucket/Image-32.jpg"
        ),
        // 04
        VibeQuote(
            part1 = "<yuyu>Every Tall friend need a short best friend</>🥴",
            part2 = "<yuyu>you SHORT!!, I'm literally older than you</>😂",
            photo1 = "$BASE_URL/bucket/Image-33.jpg",
            photo2 = "$BASE_URL/bucket/Image-34.jpg"
        ),
        // 05
        VibeQuote(
            part1 = "<rules>..and till the END you're stuck with me</>",
            part2 = "<rules>I'll always be there to irritate you</>",
            photo1 = "$BASE_URL/bucket/Image-35.jpg",
            photo2 = "$BASE_URL/bucket/Image-36.jpg"
        ),
        // 06
        VibeQuote(
            part1 = "<xmas_bundle>Thank You For Existing</>",
            part2 = "<matemasie>My Favourite Human</> ❤️",
            photo1 = "$BASE_URL/bucket/Image-39.jpg",
            photo2 = "$BASE_URL/bucket/Image-40.jpg"
        ),
        // 07 — font span: "My Wish" in Kaushan Script
        VibeQuote(
            part1 = "<god>11:11</>\n<kaushan>You are My Wish</> :)",
            part2 = "<xmas_bundle|left>☆☆</> <lifesavers|left>..counting my lucky stars</>",
            photo1 = "$BASE_URL/bucket/Image-43.jpg",
            photo2 = "$BASE_URL/bucket/Image-44.jpg"
        ),
        // 08
        VibeQuote(
            part1 = "<jealousy>sometimes u just need to do bad things with good friends</>",
            part2 = "<jealousy>The best memories comes from the bad ideas done with best friends</>",
            photo1 = "$BASE_URL/bucket/Image-49.jpg",
            photo2 = "$BASE_URL/bucket/Image-50.jpg"
        ),
        // 09
        VibeQuote(
            part1 = "<lifesavers>you are special to me</>",
            part2 = "<lifesavers>let whoever think whatever</>",
            photo1 = "$BASE_URL/bucket/Image-79.jpg",
            photo2 = "$BASE_URL/bucket/Image-80.jpg"
        ),
        // 10
        VibeQuote(
            part1 = "<lifesavers>you are crazy I like that..</>😂",
            part2 = "<god|left>REMEMBER:</>\n<lifesavers|left>you've survived every</><gamaamli>I CANT DO THIS</><lifesavers>so far</>",
            photo1 = "$BASE_URL/bucket/Image-57.jpg",
            photo2 = "$BASE_URL/bucket/Image-58.jpg"
        ),
        // 11 — font span: "wish" in Kaushan Script
       VibeQuote(
            part1 = "<god>Me</>\n<god>+ U</>\n{line}\n<god> ♥</>",
            part2 = "<god>Me</>\n<god>- U</>\n{line}\n<crayon> 🤕</>",
            photo1 = "$BASE_URL/bucket/Image-47.jpg",
            photo2 = "$BASE_URL/bucket/Image-48.jpg"
        ),
        // 12
        VibeQuote(
            part1 = "<sakelar>you are the trouble am in</>",
            part2 = "<sakelar|right>trouble never looked so goddamn fine</>",
            photo1 = "$BASE_URL/bucket/Image-67.jpg",
            photo2 = "$BASE_URL/bucket/Image-68.jpg"
        ),
        // 13
        VibeQuote(
            part1 = "<klomang>there is fire between us</>",
            part2 = "<sprucken|left>a beautiful thing is never</>\n<klomang|right>perfect</>",
            photo1 = "$BASE_URL/bucket/Image-73.jpg",
            photo2 = "$BASE_URL/bucket/Image-74.jpg"
        ),
        // 14
       VibeQuote(
            part1 = "<yuyu>avec toi, je suis moi</>",
            part2 = "<yuyu>tout pour toi..</>",
            photo1 = "$BASE_URL/bucket/Image-83.jpg",
            photo2 = "$BASE_URL/bucket/Image-84.jpg"
        ),
        // 15
        VibeQuote(
            part1 = "<sprucken>...but u can call me love</>",
            part2 = "<sprucken>I call you shawty</>",
            photo1 = "$BASE_URL/bucket/Image-85.jpg",
            photo2 = "$BASE_URL/bucket/Image-86.jpg"
        ),
        // 16
       VibeQuote(
            part1 = "<filx>you are my euphoria</>",
            part2 = "<marck|left>more than a</>\n<cookie|center>girlfriend</>\n<marck|right>u are my best friend</>",
            photo1 = "$BASE_URL/bucket/Image-87.jpg",
            photo2 = "$BASE_URL/bucket/Image-88.jpg"
        ),
        // 17
          VibeQuote(
            part1 = "<spookery|left>That's him:</>\n<shareit|right>My Moonbeam</>",
            part2 = "<spookery|left>That's her:</>\n<filx|right>Lady of the Moon</>",
            photo1 = "$BASE_URL/bucket/Image-91.jpg",
            photo2 = "$BASE_URL/bucket/Image-92.jpg"
        ),
        // 18
        VibeQuote(
            part1 = "<kaushan>love you</>\n<jealousy|center>🫲...THIIIIIS...🫱</>\n<kaushan>MUCH</>",
            part2 = "<crayon>i'm not okay can u love me extra today 🥺</>",
            photo1 = "$BASE_URL/bucket/Image-93.jpg",
            photo2 = "$BASE_URL/bucket/Image-94.jpg"
        ),
        // 19
        VibeQuote(
            part1 = "<asia>pure & raw</>",
            part2 = "<yuyu>boom ShaKaLaka</>",
            photo1 = "$BASE_URL/bucket/Image-95.jpg",
            photo2 = "$BASE_URL/bucket/Image-96.jpg"
        ),

        // ─── Actives 20–43 ───

        // 20
        VibeQuote(
            part1 = "<crayon>mh you</> <asia>stupid</> <playfair>:)</>",
            part2 = "<shadows>I'm so Youholic</>",
            photo1 = "$BASE_URL/bucket/Image-13.jpg",
            photo2 = "$BASE_URL/bucket/Image-14.jpg"
        ),
        // 21
        VibeQuote(
            part1 = "<playfair>its</> <evil>BF</>",
            part2 = "<playfair|left>No its</> <spookery>FB</>",
            photo1 = "$BASE_URL/bucket/Image-1.jpg",
            photo2 = "$BASE_URL/bucket/Image-2.JPG"
        ),
        // 22
        VibeQuote(
            part1 = "<sprucken>I need my own planet y'all irritates me</>",
            part2 = "<crayon|left>bonjour motherfvcker</>",
            photo1 = "$BASE_URL/bucket/Image-3.jpg",
            photo2 = "$BASE_URL/bucket/Image-4.JPG"
        ),
        // 23
        VibeQuote(
            part1 = "<filx>main character energy</>",
            part2 = "<marck>Bas vibe match honi chahiye</>",
            photo1 = "$BASE_URL/bucket/Image-53.jpg",
            photo2 = "$BASE_URL/bucket/Image-54.jpg"
        ),
        // 24
        VibeQuote(
            part1 = "<sprucken|right>..but is she me ?</>",
            part2 = "<sprucken>no face just vibes</>",
            photo1 = "$BASE_URL/bucket/Image-11.JPG",
            photo2 = "$BASE_URL/bucket/Image-12.JPG"
        ),
        // 25
      VibeQuote(
            part1 = "<crayon>trying to remember who you where</> <playfair>?!</>",
            part2 = "<harukaze|left>You know me .?</>",
            photo1 = "$BASE_URL/bucket/Image-75.jpg",
            photo2 = "$BASE_URL/bucket/Image-76.jpg"
        ),
        // 26
       VibeQuote(
            part1 = "<robotomono|left>Who's this</> <gamaamli>DIVA.???</>",
            part2 = "<skate>This is me.!!!</>",
            photo1 = "$BASE_URL/bucket/Image-15.jpg",
            photo2 = "$BASE_URL/bucket/Image-16.jpg"
        ),
        // 27
        VibeQuote(
            part1 = "<crayon>I hate diabetes😂😂</>",
            part2 = "<yuyu>I'll be ur sugar daddy😭!</>",
            photo1 = "$BASE_URL/bucket/Image-29.jpg",
            photo2 = "$BASE_URL/bucket/Image-30.jpg"
        ),
        // 28
         VibeQuote(
            part1 = "<playfair|left>more than friends...</>",
            part2 = "<sprucken|right>... less than a couple</>",
            photo1 = "$BASE_URL/bucket/Image-37.jpg",
            photo2 = "$BASE_URL/bucket/Image-38.jpg"
        ),
        // 29
        VibeQuote(
            part1 = "<crayon|left>yess continue</> <playfair>...!</>",
            part2 = "<shareit>Do you understand..??</>",
            photo1 = "$BASE_URL/bucket/Image-59.jpg",
            photo2 = "$BASE_URL/bucket/Image-60.jpg"
        ),
        // 30
       VibeQuote(
            part1 = "<matemasie>miss me yet..?!</>",
            part2 = "<sprucken|left>you dont cross my mind u live in it..</>",
            photo1 = "$BASE_URL/bucket/Image-5.JPG",
            photo2 = "$BASE_URL/bucket/Image-6.jpg"
        ),
        // 31
        VibeQuote(
            part1 = "<shadows>Fvck butterflies</>",
            part2 = "<crayon|left>I feel the whole</> <matemasie>zoo</> <crayon>when i'm with you</>",
            photo1 = "$BASE_URL/bucket/Image-7.JPG",
            photo2 = "$BASE_URL/bucket/Image-8.JPG"
        ),
        // 32
        VibeQuote(
            part1 = "<crayon|left>we're just friends trying like hell not to be</> <asia>lovers</>",
            part2 = "<sprucken|left>we play dump but we know exactly what we're doing</>",
            photo1 = "$BASE_URL/bucket/Image-77.jpg",
            photo2 = "$BASE_URL/bucket/Image-78.jpg"
        ),
        // 33 — font span: "Love" in Playfair Display
       VibeQuote(
            part1 = "<filx|right>Heart says,</> <playfair>\"Love\"</>",
            part2 = "<sakelar|left>Mind says,</> <playfair>\"Friends\"</>",
            photo1 = "$BASE_URL/bucket/Image-19.jpg",
            photo2 = "$BASE_URL/bucket/Image-20.jpg"
        ),
        // 34
        VibeQuote(
            part1 = "<filx>By the way i'm wearing the</> <lifesavers>smile</> <filx>u gave me.</>",
            part2 = "<dancing>it takes me days to recover after a dream with u in it</>",
            photo1 = "$BASE_URL/bucket/Image-17.jpg",
            photo2 = "$BASE_URL/bucket/Image-18.jpg"
        ),
        // 35
         VibeQuote(
            part1 = "<sprucken|left>You'll always be my favorite</>\n<evil|right>\"WHAT IF\"</>",
            part2 = "<trash|left>\"just friends\"</>\n\n<sprucken|right>became our favorite lie</>",
            photo1 = "$BASE_URL/bucket/Image-41.jpg",
            photo2 = "$BASE_URL/bucket/Image-42.jpg"
        ),
        // 36
       VibeQuote(
            part1 = "<marker>I didn't fall for u, you fvcking tripped me</>",
            part2 = "<jealousy>I wonder what I look like in your eyes.?</>",
            photo1 = "$BASE_URL/bucket/Image-89.jpg",
            photo2 = "$BASE_URL/bucket/Image-90.jpg"
        ),
        // 37
         VibeQuote(
            part1 = "<crayon>I'd be his if he asked, but he never will</>",
            part2 = "<lifesavers|left>Dating or not.. you're still</> <matemasie>mine</>",
            photo1 = "$BASE_URL/bucket/Image-45.jpg",
            photo2 = "$BASE_URL/bucket/Image-46.jpg"
        ),
        // 38
        VibeQuote(
            part1 = "<harukaze>you confuse me 🥴</>",
            part2 = "<asia|left>you and I will always be unfinished business</>",
            photo1 = "$BASE_URL/bucket/Image-51.jpg",
            photo2 = "$BASE_URL/bucket/Image-52.jpg"
        ),
        // 39
       VibeQuote(
            part1 = "<trash|left>Secrete</> <evil>#7:</>\n<sakelar|left>I want you so be brave an' want me back</>",
            part2 = "<trash|left>Secrete</> <evil>#6:</>\n<sakelar|left>I stare at you when you're not looking.</>",
            photo1 = "$BASE_URL/bucket/Image-61.jpg",
            photo2 = "$BASE_URL/bucket/Image-62.jpg"
        ),
        // 40
        VibeQuote(
            part1 = "<crayon>I cant unlove you</>",
            part2 = "<crayon>real feelings dont just go away</>",
            photo1 = "$BASE_URL/bucket/Image-65.jpg",
            photo2 = "$BASE_URL/bucket/Image-66.jpg"
        ),
        // 41
         VibeQuote(
            part1 = "<sprucken|right>I wish he loved me like I love him</>",
            part2 = "<crayon|left>plot twist:</> <sprucken>he loves you more</>",
            photo1 = "$BASE_URL/bucket/Image-63.jpg",
            photo2 = "$BASE_URL/bucket/Image-64.jpg"
        ),
        // 42
        VibeQuote(
            part1 = "<kaushan|left>You and I are one big</> <lifesavers>\"its complicated\"</>",
            part2 = "<lifesavers>c'mon, we'd look cute together</>",
            photo1 = "$BASE_URL/bucket/Image-69.jpg",
            photo2 = "$BASE_URL/bucket/Image-70.jpg"
        ),
        //43
          VibeQuote(
            part1 = "<hello_xmas>some kisses are given with the eyes</>",
            part2 = "<lifesavers>Damn your eyes !?!</>",
            photo1 = "$BASE_URL/bucket/Image-56.jpg",
            photo2 = "$BASE_URL/bucket/Image-55.jpg"
        ),
        // 44
        VibeQuote(
            part1 = "<marck>More than a friendship less than a relationship</>",
            part2 = "<klomang>Flirtationship;</>",
            photo1 = "$BASE_URL/bucket/Image-71.jpg",
            photo2 = "$BASE_URL/bucket/Image-72.jpg"
        )*/

    )
}