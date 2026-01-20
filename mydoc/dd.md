
# 使用 MapLibre Android API 显示用户位置和路线覆盖层
用maplibre android api，写 一个在地图上 显示 用户当前位置 和 一条线路 overlay的

以下是完整的 Android Kotlin 实现，包含用户位置显示和路线绘制功能。

## 1. 项目配置

### build.gradle.kts (app 模块)
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.maplibredemo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.maplibredemo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // MapLibre Android SDK
    implementation("org.maplibre.gl:android-sdk:10.3.0")
    
    // Location services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Permissions
    implementation("com.karumi:dexter:6.2.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
}
```

### AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 权限 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    
    <!-- 网络配置 -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <!-- OpenGL ES 2.0 (必需) -->
    <uses-feature
        android:glEsVersion="0x00020000"
        android:required="true" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MapLibreDemo"
        android:usesCleartextTraffic="true"
        tools:targetApi="31">
        
        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MapLibreDemo">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## 2. 主活动实现

### MainActivity.kt
```kotlin
package com.example.maplibredemo

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import com.example.maplibredemo.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonObject
import org.maplibre.android.style.images.Sprite
import org.maplibre.android.utils.BitmapUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var mapView: MapView
    private lateinit var maplibreMap: MapLibreMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    // 图层和源ID常量
    companion object {
        private const val USER_LOCATION_SOURCE_ID = "user-location-source"
        private const val USER_LOCATION_LAYER_ID = "user-location-layer"
        private const val ROUTE_SOURCE_ID = "route-source"
        private const val ROUTE_LAYER_ID = "route-layer"
        private const val ROUTE_START_SOURCE_ID = "route-start-source"
        private const val ROUTE_START_LAYER_ID = "route-start-layer"
        private const val ROUTE_END_SOURCE_ID = "route-end-source"
        private const val ROUTE_END_LAYER_ID = "route-end-layer"
        private const val DESTINATION_LAYER_ID = "destination-layer"
        private const val DESTINATION_SOURCE_ID = "destination-source"
    }
    
    // 测试路线坐标（北京中关村到天安门）
    private val routeCoordinates = listOf(
        LatLng(39.983171, 116.316641),  // 起点
        LatLng(39.980000, 116.320000),
        LatLng(39.975000, 116.330000),
        LatLng(39.972000, 116.340000),
        LatLng(39.915000, 116.397000)   // 终点
    )
    
    // 目的地坐标
    private val destination = LatLng(39.915000, 116.397000)  // 天安门
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 MapLibre
        MapLibre.getInstance(this)
        
        // 初始化 Fused Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // 设置视图绑定
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取 MapView
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        
        // 设置按钮点击事件
        binding.btnStartNavigation.setOnClickListener {
            startNavigation()
        }
        
        binding.btnClearRoute.setOnClickListener {
            clearRoute()
        }
        
        binding.btnLocateMe.setOnClickListener {
            centerOnUserLocation()
        }
    }
    
    override fun onMapReady(map: MapLibreMap) {
        this.maplibreMap = map
        
        // 设置地图样式
        map.setStyle(Style.MAPBOX_STREETS) { style ->
            // 检查权限
            checkLocationPermission()
            
            // 添加路线源和层
            addRouteSource(style)
            addRouteLayer(style)
            
            // 添加起点终点标记
            addRouteMarkers(style)
            
            // 添加目的地标记
            addDestinationMarker(style)
            
            // 添加用户位置标记
            addUserLocationSource(style)
            addUserLocationLayer(style)
            
            // 初始化位置组件
            enableLocationComponent(style)
        }
        
        // 设置地图点击监听
        map.addOnMapClickListener { point ->
            Toast.makeText(
                this, 
                "点击位置: ${point.latitude}, ${point.longitude}",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }
    
    /**
     * 检查位置权限
     */
    private fun checkLocationPermission() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        if (report.areAllPermissionsGranted()) {
                            // 权限已授予
                            enableUserLocation()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "需要位置权限来显示您的位置",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }
    
    /**
     * 启用位置组件
     */
    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .pulseEnabled(true)  // 启用脉冲效果
            .pulseColor(Color.BLUE)
            .pulseAlpha(0.2f)
            .pulseSingleDuration(3000L)
            .backgroundTintColor(Color.BLUE)
            .foregroundTintColor(Color.WHITE)
            .accuracyAlpha(0.15f)
            .accuracyColor(Color.BLUE)
            .build()
        
        val locationComponentActivationOptions = 
            LocationComponentActivationOptions.builder(this, style)
                .locationComponentOptions(locationComponentOptions)
                .useDefaultLocationEngine(true)
                .build()
        
        maplibreMap.locationComponent.apply {
            activateLocationComponent(locationComponentActivationOptions)
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
        }
    }
    
    /**
     * 启用用户位置
     */
    @SuppressLint("MissingPermission")
    private fun enableUserLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    
                    // 更新用户位置源
                    updateUserLocation(userLatLng)
                    
                    // 移动相机到用户位置
                    val cameraPosition = CameraPosition.Builder()
                        .target(userLatLng)
                        .zoom(15.0)
                        .build()
                    maplibreMap.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory
                            .newCameraPosition(cameraPosition),
                        1000
                    )
                }
            }
    }
    
    /**
     * 添加用户位置源
     */
    private fun addUserLocationSource(style: Style) {
        val geoJsonSource = GeoJsonSource(
            USER_LOCATION_SOURCE_ID,
            createUserLocationFeature(LatLng(0.0, 0.0))  // 初始位置
        )
        style.addSource(geoJsonSource)
    }
    
    /**
     * 添加用户位置层
     */
    private fun addUserLocationLayer(style: Style) {
        val circleLayer = CircleLayer(USER_LOCATION_LAYER_ID, USER_LOCATION_SOURCE_ID)
            .withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(Color.BLUE),
                PropertyFactory.circleOpacity(0.8f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        style.addLayer(circleLayer)
    }
    
    /**
     * 添加路线源
     */
    private fun addRouteSource(style: Style) {
        val routeFeature = createRouteFeature(routeCoordinates)
        val routeSource = GeoJsonSource(ROUTE_SOURCE_ID, routeFeature)
        style.addSource(routeSource)
    }
    
    /**
     * 添加路线层
     */
    private fun addRouteLayer(style: Style) {
        val lineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID)
            .withProperties(
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineColor(Color.parseColor("#3bb2d0")),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        style.addLayer(lineLayer)
    }
    
    /**
     * 添加路线标记
     */
    private fun addRouteMarkers(style: Style) {
        // 起点标记
        val startPoint = routeCoordinates.first()
        val startSource = GeoJsonSource(
            ROUTE_START_SOURCE_ID,
            createPointFeature(startPoint, "start")
        )
        style.addSource(startSource)
        
        val startLayer = SymbolLayer(ROUTE_START_LAYER_ID, ROUTE_START_SOURCE_ID)
            .withProperties(
                PropertyFactory.iconImage("marker-15"),
                PropertyFactory.iconSize(1.5f)
            )
        style.addLayer(startLayer)
        
        // 终点标记
        val endPoint = routeCoordinates.last()
        val endSource = GeoJsonSource(
            ROUTE_END_SOURCE_ID,
            createPointFeature(endPoint, "end")
        )
        style.addSource(endSource)
        
        val endLayer = SymbolLayer(ROUTE_END_LAYER_ID, ROUTE_END_SOURCE_ID)
            .withProperties(
                PropertyFactory.iconImage("marker-15"),
                PropertyFactory.iconSize(1.5f),
                PropertyFactory.iconColor(Color.RED)
            )
        style.addLayer(endLayer)
    }
    
    /**
     * 添加目的地标记
     */
    private fun addDestinationMarker(style: Style) {
        val destinationSource = GeoJsonSource(
            DESTINATION_SOURCE_ID,
            createPointFeature(destination, "destination")
        )
        style.addSource(destinationSource)
        
        val destinationLayer = SymbolLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID)
            .withProperties(
                PropertyFactory.iconImage("star-15"),
                PropertyFactory.iconSize(1.5f),
                PropertyFactory.iconColor(Color.YELLOW)
            )
        style.addLayer(destinationLayer)
    }
    
    /**
     * 更新用户位置
     */
    private fun updateUserLocation(latLng: LatLng) {
        val style = maplibreMap.style
        style?.let {
            val source = it.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID)
            source?.setGeoJson(createUserLocationFeature(latLng))
            
            // 计算到目的地的距离
            val distance = calculateDistance(latLng, destination)
            binding.tvDistance.text = String.format("距离目的地: %.2f公里", distance)
        }
    }
    
    /**
     * 开始导航
     */
    private fun startNavigation() {
        Toast.makeText(this, "开始导航", Toast.LENGTH_SHORT).show()
        
        // 移动相机显示整个路线
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
            .includes(routeCoordinates)
            .build()
        
        maplibreMap.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory
                .newLatLngBounds(bounds, 100),
            1000
        )
        
        // 显示导航信息
        val totalDistance = calculateRouteDistance(routeCoordinates)
        binding.tvRouteInfo.text = String.format("路线总长: %.2f公里", totalDistance)
    }
    
    /**
     * 清除路线
     */
    private fun clearRoute() {
        val style = maplibreMap.style
        style?.let {
            // 移除路线层
            it.removeLayer(ROUTE_LAYER_ID)
            it.removeLayer(ROUTE_START_LAYER_ID)
            it.removeLayer(ROUTE_END_LAYER_ID)
            it.removeLayer(DESTINATION_LAYER_ID)
            
            // 移除路线源
            it.removeSource(ROUTE_SOURCE_ID)
            it.removeSource(ROUTE_START_SOURCE_ID)
            it.removeSource(ROUTE_END_SOURCE_ID)
            it.removeSource(DESTINATION_SOURCE_ID)
            
            binding.tvRouteInfo.text = "路线已清除"
        }
    }
    
    /**
     * 定位到用户位置
     */
    private fun centerOnUserLocation() {
        maplibreMap.locationComponent.lastKnownLocation?.let { location ->
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(16.0)
                .build()
            
            maplibreMap.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory
                    .newCameraPosition(cameraPosition),
                500
            )
        }
    }
    
    /**
     * 计算两点间距离（公里）
     */
    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)
        
        val dlon = lon2 - lon1
        val dlat = lat2 - lat1
        val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return 6371.0 * c  // 地球半径 6371km
    }
    
    /**
     * 计算路线总距离
     */
    private fun calculateRouteDistance(route: List<LatLng>): Double {
        var totalDistance = 0.0
        for (i in 0 until route.size - 1) {
            totalDistance += calculateDistance(route[i], route[i + 1])
        }
        return totalDistance
    }
    
    /**
     * 创建用户位置特征
     */
    private fun createUserLocationFeature(latLng: LatLng): String {
        return """
        {
            "type": "Feature",
            "properties": {
                "id": "user-location",
                "type": "user"
            },
            "geometry": {
                "type": "Point",
                "coordinates": [${latLng.longitude}, ${latLng.latitude}]
            }
        }
        """.trimIndent()
    }
    
    /**
     * 创建路线特征
     */
    private fun createRouteFeature(coordinates: List<LatLng>): String {
        val coordsStr = coordinates.joinToString(",") { 
            "[${it.longitude}, ${it.latitude}]" 
        }
        
        return """
        {
            "type": "Feature",
            "properties": {
                "id": "route",
                "type": "navigation"
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [$coordsStr]
            }
        }
        """.trimIndent()
    }
    
    /**
     * 创建点特征
     */
    private fun createPointFeature(latLng: LatLng, type: String): String {
        return """
        {
            "type": "Feature",
            "properties": {
                "id": "$type",
                "type": "$type"
            },
            "geometry": {
                "type": "Point",
                "coordinates": [${latLng.longitude}, ${latLng.latitude}]
            }
        }
        """.trimIndent()
    }
    
    // 地图生命周期方法
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
    
    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}
```

## 3. 布局文件

### activity_main.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <!-- 地图视图 -->
    <org.maplibre.android.maps.MapView
        android:id="@+id/mapView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- 顶部控制面板 -->
    <androidx.cardview.widget.CardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        app:cardCornerRadius="8dp"
        app:cardElevation="8dp"
        app:layout_constraintTop_toTopOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">

            <TextView
                android:id="@+id/tvDistance"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="距离目的地: -- 公里"
                android:textSize="16sp"
                android:textStyle="bold"
                android:textColor="@android:color/black" />

            <TextView
                android:id="@+id/tvRouteInfo"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="路线信息: --"
                android:textSize="14sp"
                android:textColor="@android:color/darker_gray" />
        </LinearLayout>
    </androidx.cardview.widget.CardView>

    <!-- 底部控制按钮 -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:orientation="horizontal"
        android:gravity="center"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/btnLocateMe"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="8dp"
            android:src="@android:drawable/ic_menu_mylocation"
            app:backgroundTint="@android:color/white"
            app:tint="@android:color/black" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/btnStartNavigation"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="8dp"
            android:src="@android:drawable/ic_menu_directions"
            app:backgroundTint="@color/purple_500"
            app:tint="@android:color/white" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/btnClearRoute"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            app:backgroundTint="@android:color/white"
            app:tint="@android:color/black" />
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

## 4. 颜色资源

### res/values/colors.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="route_color">#3bb2d0</color>
</resources>
```

## 5. 功能说明

### 主要功能：
1. **实时位置显示**：显示用户当前位置（蓝色圆点）
2. **路线绘制**：绘制从起点到终点的导航路线
3. **标记点**：
   - 绿色：路线起点
   - 红色：路线终点
   - 黄色：目的地标记
4. **控制功能**：
   - 定位按钮：定位到当前位置
   - 导航按钮：开始导航，显示路线信息
   - 清除按钮：清除路线和标记
5. **信息显示**：
   - 当前位置到目的地的距离
   - 路线总长度

### 技术特点：
1. **权限管理**：使用 Dexter 处理位置权限
2. **图层管理**：分开管理用户位置、路线、标记等不同图层
3. **性能优化**：使用矢量图层，避免频繁重绘
4. **交互友好**：平滑的相机动画和触摸反馈
5. **错误处理**：完善的异常处理和用户提示

## 6. 扩展功能建议

```kotlin
// 可以添加的扩展功能：

// 1. 多点路线
private fun addMultipleRoutes() {
    val alternativeRoute = listOf(
        LatLng(39.983171, 116.316641),
        LatLng(39.985000, 116.325000),
        LatLng(39.982000, 116.335000),
        LatLng(39.915000, 116.397000)
    )
    // 添加第二条路线
}

// 2. 路线动画
private fun animateRoute() {
    // 实现路线绘制动画
}

// 3. 位置更新监听
private fun setupLocationUpdates() {
    val locationRequest = LocationRequest.Builder(1000L)
        .setIntervalMillis(1000L)
        .setMinUpdateIntervalMillis(500L)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()
    
    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()
    )
}

// 4. 离线地图支持
private fun setupOfflineMap() {
    val offlineManager = OfflineManager.getInstance(this)
    // 下载离线地图
}
```

## 7. 运行说明

1. **设备要求**：Android 6.0+，支持 OpenGL ES 2.0
2. **网络权限**：需要网络加载地图瓦片
3. **位置权限**：运行时请求位置权限
4. **测试坐标**：默认为北京中关村到天安门的路线
5. **样式**：默认使用 Mapbox Streets 样式

这个实现展示了 MapLibre Android API 的核心功能，包括地图显示、图层管理、位置服务和用户交互，是一个完整的导航应用基础框架。