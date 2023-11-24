.class public Lcom/zola/bmi/BMIMain;
.super Landroid/support/v7/app/AppCompatActivity;
.source "BMIMain.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/zola/bmi/BMIMain$PlaceholderFragment;
    }
.end annotation


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 16
    invoke-direct {p0}, Landroid/support/v7/app/AppCompatActivity;-><init>()V

    return-void
.end method

.method private calculateBMI(DD)D
    .locals 2

    const-wide v0, 0x4001a305532617c2L    # 2.2046

    div-double/2addr p1, v0

    const-wide v0, 0x3f9a027525460aa6L    # 0.0254

    mul-double p3, p3, v0

    div-double/2addr p1, p3

    div-double/2addr p1, p3

    return-wide p1
.end method

.method private interpretBMI(D)Ljava/lang/String;
    .locals 3

    const-wide/16 v0, 0x0

    cmpl-double v2, p1, v0

    if-nez v2, :cond_0

    const-string p1, "Enter your details"

    return-object p1

    :cond_0
    const-wide v0, 0x4032800000000000L    # 18.5

    cmpg-double v2, p1, v0

    if-gez v2, :cond_1

    const-string p1, "You are underweight"

    return-object p1

    :cond_1
    const-wide/high16 v0, 0x4039000000000000L    # 25.0

    cmpg-double v2, p1, v0

    if-gez v2, :cond_2

    const-string p1, "You are normal weight"

    return-object p1

    :cond_2
    const-wide/high16 v0, 0x403e000000000000L    # 30.0

    cmpg-double v2, p1, v0

    if-gez v2, :cond_3

    const-string p1, "You are overweight"

    return-object p1

    :cond_3
    const-wide/high16 v0, 0x4044000000000000L    # 40.0

    cmpg-double v2, p1, v0

    if-gez v2, :cond_4

    const-string p1, "You are obese"

    return-object p1

    :cond_4
    const-string p1, "You are severely obese"

    return-object p1
.end method


# virtual methods
.method public calculateClickHandler(Landroid/view/View;)V
    .locals 13

    .line 32
    invoke-virtual {p1}, Landroid/view/View;->getId()I

    move-result p1

    const v0, 0x7f080023

    if-ne p1, v0, :cond_5

    const p1, 0x7f080089

    .line 37
    invoke-virtual {p0, p1}, Lcom/zola/bmi/BMIMain;->findViewById(I)Landroid/view/View;

    move-result-object p1

    check-cast p1, Landroid/widget/EditText;

    const v0, 0x7f08003d

    .line 38
    invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->findViewById(I)Landroid/view/View;

    move-result-object v0

    check-cast v0, Landroid/widget/EditText;

    const v1, 0x7f080059

    .line 39
    invoke-virtual {p0, v1}, Lcom/zola/bmi/BMIMain;->findViewById(I)Landroid/view/View;

    move-result-object v1

    check-cast v1, Landroid/widget/TextView;

    const v2, 0x7f08008a

    .line 41
    invoke-virtual {p0, v2}, Lcom/zola/bmi/BMIMain;->findViewById(I)Landroid/view/View;

    move-result-object v2

    check-cast v2, Landroid/widget/Spinner;

    const v3, 0x7f08003e

    .line 42
    invoke-virtual {p0, v3}, Lcom/zola/bmi/BMIMain;->findViewById(I)Landroid/view/View;

    move-result-object v3

    check-cast v3, Landroid/widget/Spinner;

    .line 43
    invoke-virtual {v2}, Landroid/widget/Spinner;->getSelectedItem()Ljava/lang/Object;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v2

    .line 44
    invoke-virtual {v3}, Landroid/widget/Spinner;->getSelectedItem()Ljava/lang/Object;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v3

    .line 52
    invoke-virtual {p1}, Landroid/widget/EditText;->getText()Landroid/text/Editable;

    move-result-object v4

    invoke-virtual {v4}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v4

    const-string v5, ""

    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    const-wide/16 v5, 0x0

    if-nez v4, :cond_0

    .line 53
    invoke-virtual {p1}, Landroid/widget/EditText;->getText()Landroid/text/Editable;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Ljava/lang/Double;->parseDouble(Ljava/lang/String;)D

    move-result-wide v7

    goto :goto_0

    :cond_0
    move-wide v7, v5

    .line 56
    :goto_0
    invoke-virtual {v0}, Landroid/widget/EditText;->getText()Landroid/text/Editable;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p1

    const-string v4, ""

    invoke-virtual {p1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_1

    .line 57
    invoke-virtual {v0}, Landroid/widget/EditText;->getText()Landroid/text/Editable;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Ljava/lang/Double;->parseDouble(Ljava/lang/String;)D

    move-result-wide v5

    :cond_1
    const-string p1, "Pounds"

    .line 63
    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_2

    const-string p1, "Inches"

    invoke-virtual {v3, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_2

    .line 64
    invoke-direct {p0, v7, v8, v5, v6}, Lcom/zola/bmi/BMIMain;->calculateBMI(DD)D

    move-result-wide v2

    goto :goto_1

    :cond_2
    const-string p1, "Kilograms"

    .line 65
    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    const-wide v9, 0x4001a3d70a3d70a4L    # 2.205

    if-eqz p1, :cond_3

    const-string p1, "Inches"

    .line 66
    invoke-virtual {v3, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_3

    mul-double v7, v7, v9

    .line 68
    invoke-direct {p0, v7, v8, v5, v6}, Lcom/zola/bmi/BMIMain;->calculateBMI(DD)D

    move-result-wide v2

    goto :goto_1

    :cond_3
    const-string p1, "Pounds"

    .line 69
    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    const-wide v11, 0x400451eb851eb852L    # 2.54

    if-eqz p1, :cond_4

    const-string p1, "Centimetres"

    invoke-virtual {v3, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_4

    div-double/2addr v5, v11

    .line 71
    invoke-direct {p0, v7, v8, v5, v6}, Lcom/zola/bmi/BMIMain;->calculateBMI(DD)D

    move-result-wide v2

    goto :goto_1

    :cond_4
    mul-double v7, v7, v9

    div-double/2addr v5, v11

    .line 75
    invoke-direct {p0, v7, v8, v5, v6}, Lcom/zola/bmi/BMIMain;->calculateBMI(DD)D

    move-result-wide v2

    :goto_1
    const-wide/high16 v4, 0x4024000000000000L    # 10.0

    mul-double v2, v2, v4

    .line 79
    invoke-static {v2, v3}, Ljava/lang/Math;->round(D)J

    move-result-wide v2

    long-to-double v2, v2

    invoke-static {v2, v3}, Ljava/lang/Double;->isNaN(D)Z

    div-double/2addr v2, v4

    .line 80
    new-instance p1, Ljava/text/DecimalFormat;

    const-string v0, "##.0"

    invoke-direct {p1, v0}, Ljava/text/DecimalFormat;-><init>(Ljava/lang/String;)V

    .line 83
    invoke-direct {p0, v2, v3}, Lcom/zola/bmi/BMIMain;->interpretBMI(D)Ljava/lang/String;

    move-result-object p1

    .line 86
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "BMI Score = "

    invoke-virtual {v0, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, v2, v3}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    const-string v2, "\n"

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    :cond_5
    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 2

    .line 20
    invoke-super {p0, p1}, Landroid/support/v7/app/AppCompatActivity;->onCreate(Landroid/os/Bundle;)V

    const v0, 0x7f0a001c

    .line 21
    invoke-virtual {p0, v0}, Lcom/zola/bmi/BMIMain;->setContentView(I)V

    if-nez p1, :cond_0

    .line 24
    invoke-virtual {p0}, Lcom/zola/bmi/BMIMain;->getSupportFragmentManager()Landroid/support/v4/app/FragmentManager;

    move-result-object p1

    invoke-virtual {p1}, Landroid/support/v4/app/FragmentManager;->beginTransaction()Landroid/support/v4/app/FragmentTransaction;

    move-result-object p1

    const v0, 0x7f08002c

    new-instance v1, Lcom/zola/bmi/BMIMain$PlaceholderFragment;

    invoke-direct {v1}, Lcom/zola/bmi/BMIMain$PlaceholderFragment;-><init>()V

    .line 25
    invoke-virtual {p1, v0, v1}, Landroid/support/v4/app/FragmentTransaction;->add(ILandroid/support/v4/app/Fragment;)Landroid/support/v4/app/FragmentTransaction;

    move-result-object p1

    .line 26
    invoke-virtual {p1}, Landroid/support/v4/app/FragmentTransaction;->commit()I

    :cond_0
    return-void
.end method
