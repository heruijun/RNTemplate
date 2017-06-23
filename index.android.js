/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 * @flow
 */

import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  Image,
  View
} from 'react-native';
import LocalImg from './img'
import Permissions from 'react-native-permissions'

export default class RNTemplate extends Component {
  constructor(props) {
    super(props)
    this.state = {
      avatarSource: LocalImg.avatar
    }
  }

  render() {
    return (
      <View style={styles.container}>
        <Image source={this.state.avatarSource} style={{width: 100, height: 100, borderRadius: 10}}/>
        <Text style={styles.welcome}>
          RN热更新222!
        </Text>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
});

AppRegistry.registerComponent('RNTemplate', () => RNTemplate);
