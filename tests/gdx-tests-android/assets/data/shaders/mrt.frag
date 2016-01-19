#version 300 es

#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_diffuseTexture;
uniform sampler2D u_specularTexture;
uniform sampler2D u_normalTexture;

in vec3 v_normal;
in vec3 v_tangent;
in vec3 v_binormal;
in vec3 v_position;
in vec2 v_texCoords;

layout(location = 0) out vec4 diffuseOut;
layout(location = 1) out vec3 normalOut;
layout(location = 2) out vec3 positionOut;

void main() {

    vec4 diffuse = texture2D(u_diffuseTexture, v_texCoords);
    vec4 specular = texture2D(u_specularTexture, v_texCoords);
    vec3 normal = normalize(2.0 * texture2D(u_normalTexture, v_texCoords).xyz - 1.0);

    diffuseOut.rgb = diffuse.rgb;
    diffuseOut.a = specular.r;

    vec3 finnormal = normalize((v_tangent * normal.x) + (v_binormal * normal.y) + (v_normal * normal.z));

    normalOut = finnormal;
    positionOut = v_position;
}
