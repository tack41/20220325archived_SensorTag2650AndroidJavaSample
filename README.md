**2022/3/25運用停止**

## これは何
SensorTag CC2650のセンサー値をAndroidのJavaで取得するためのサンプルアプリです。

## メモ
2018/1/6現在、手持ちのREV.1.5.1ではTemperature,Humidityは取得できませんでした。

http://processors.wiki.ti.com/index.php/CC2650_SensorTag_User%27s_Guide
によるとAMP007が実装されていないのでこれによる温度取得はできないとあるのですが、代替手段として提示されているHumidityまでも取得できないのでどうにもなりませんでした。
Barometerの下位3bitがTemperatureとあるのですが、どのように変換すれば利用できるのかわかりませんでした。
